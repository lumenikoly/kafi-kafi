package com.lightkafka.core.kafka

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

interface KafkaConsumerService {
    fun startSession(request: ConsumerSessionRequest): Flow<ConsumerEvent>

    suspend fun pause()

    suspend fun resume()

    suspend fun commit(): KafkaResult<Unit>

    suspend fun stop()

    suspend fun close()
}

class DefaultKafkaConsumerService(
    private val connectionConfig: KafkaConnectionConfig,
    private val clientFactory: KafkaConsumerClientFactory = defaultKafkaConsumerClientFactory,
    private val operationTimeout: Duration = Duration.ofSeconds(10),
) : KafkaConsumerService {
    private val activeSessionMutex = Mutex()
    private var activeSession: ActiveSession? = null

    override fun startSession(request: ConsumerSessionRequest): Flow<ConsumerEvent> =
        flow {
            val session = ActiveSession(clientFactory.create(connectionConfig, request))
            activateSession(session)

            try {
                when (val initialization = initializeSession(session, request)) {
                    is KafkaResult.Failure -> {
                        emit(ConsumerEvent.Error(initialization.error))
                        return@flow
                    }

                    is KafkaResult.Success -> Unit
                }

                while (currentCoroutineContext().isActive && session.running.get()) {
                    if (session.paused.get()) {
                        delay(request.pollTimeout.toMillis().coerceAtLeast(1))
                        continue
                    }

                    when (
                        val pollResult =
                            runWithKafkaResult(
                                operation = "poll messages",
                                timeout = operationTimeout,
                            ) {
                                session.client.poll(request.pollTimeout)
                            }
                    ) {
                        is KafkaResult.Failure -> {
                            emit(ConsumerEvent.Error(pollResult.error))
                        }

                        is KafkaResult.Success -> {
                            pollResult.value.forEach { message ->
                                emit(ConsumerEvent.MessageReceived(message))
                            }
                            emit(
                                ConsumerEvent.Stats(
                                    polledRecords = pollResult.value.size,
                                ),
                            )
                            if (pollResult.value.isEmpty()) {
                                delay(request.pollTimeout.toMillis().coerceAtLeast(1))
                            }
                        }
                    }
                }
            } finally {
                clearSession(session)
            }
        }

    override suspend fun pause() {
        val session = activeSessionOrNull() ?: return
        session.paused.set(true)
        val partitions = session.assignedPartitions.get()
        runWithKafkaResult(operation = "pause consumer", timeout = operationTimeout) {
            session.client.pause(partitions)
        }
    }

    override suspend fun resume() {
        val session = activeSessionOrNull() ?: return
        session.paused.set(false)
        val partitions = session.assignedPartitions.get()
        runWithKafkaResult(operation = "resume consumer", timeout = operationTimeout) {
            session.client.resume(partitions)
        }
    }

    override suspend fun commit(): KafkaResult<Unit> {
        val session =
            activeSessionOrNull()
                ?: return KafkaResult.Failure(
                    KafkaServiceError.OperationFailed(
                        operation = "commit offsets",
                        reason = "No active consumer session",
                    ),
                )

        return runWithKafkaResult(operation = "commit offsets", timeout = operationTimeout) {
            session.client.commit()
        }
    }

    override suspend fun stop() {
        val session =
            activeSessionMutex.withLock {
                val current = activeSession
                activeSession = null
                current
            }

        session?.running?.set(false)
        session?.close()
    }

    override suspend fun close() {
        stop()
    }

    private suspend fun initializeSession(
        session: ActiveSession,
        request: ConsumerSessionRequest,
    ): KafkaResult<Unit> {
        val partitions =
            when (request.partitions) {
                null -> {
                    when (
                        val partitionsResult =
                            runWithKafkaResult(
                                operation = "resolve topic partitions",
                                timeout = operationTimeout,
                            ) {
                                session.client.resolvePartitions(request.topic)
                            }
                    ) {
                        is KafkaResult.Failure -> return partitionsResult
                        is KafkaResult.Success -> partitionsResult.value
                    }
                }

                else -> request.partitions
            }

        if (partitions.isEmpty()) {
            return KafkaResult.Failure(
                KafkaServiceError.OperationFailed(
                    operation = "initialize consumer session",
                    reason = "No partitions available for topic ${request.topic}",
                ),
            )
        }

        session.assignedPartitions.set(partitions)

        when (
            val assignResult =
                runWithKafkaResult(
                    operation = "assign partitions",
                    timeout = operationTimeout,
                ) {
                    session.client.assign(request.topic, partitions)
                }
        ) {
            is KafkaResult.Failure -> return assignResult
            is KafkaResult.Success -> Unit
        }

        return when (val startPosition = request.startPosition) {
            ConsumerStartPosition.Earliest -> {
                runWithKafkaResult(
                    operation = "seek to earliest offsets",
                    timeout = operationTimeout,
                ) {
                    session.client.seekToBeginning(partitions)
                }
            }

            ConsumerStartPosition.Latest -> {
                runWithKafkaResult(
                    operation = "seek to latest offsets",
                    timeout = operationTimeout,
                ) {
                    session.client.seekToEnd(partitions)
                }
            }

            is ConsumerStartPosition.SpecificOffsets -> {
                runWithKafkaResult(
                    operation = "seek to specific offsets",
                    timeout = operationTimeout,
                ) {
                    session.client.seekToOffsets(startPosition.offsets)
                }
            }

            is ConsumerStartPosition.Timestamp -> {
                runWithKafkaResult(
                    operation = "seek to timestamp",
                    timeout = operationTimeout,
                ) {
                    session.client.seekToTimestamp(startPosition.timestampEpochMillis, partitions)
                }
            }
        }
    }

    private suspend fun activateSession(newSession: ActiveSession) {
        val previousSession =
            activeSessionMutex.withLock {
                val previous = activeSession
                activeSession = newSession
                previous
            }

        previousSession?.running?.set(false)
        previousSession?.close()
    }

    private suspend fun clearSession(session: ActiveSession) {
        activeSessionMutex.withLock {
            if (activeSession === session) {
                activeSession = null
            }
        }
        session.running.set(false)
        session.close()
    }

    private suspend fun activeSessionOrNull(): ActiveSession? = activeSessionMutex.withLock { activeSession }

    private class ActiveSession(
        val client: KafkaConsumerClient,
    ) {
        val running: AtomicBoolean = AtomicBoolean(true)
        val paused: AtomicBoolean = AtomicBoolean(false)
        val assignedPartitions: AtomicReference<Set<Int>> = AtomicReference(emptySet())

        private val closed: AtomicBoolean = AtomicBoolean(false)

        suspend fun close() {
            if (closed.compareAndSet(false, true)) {
                client.close()
            }
        }
    }
}
