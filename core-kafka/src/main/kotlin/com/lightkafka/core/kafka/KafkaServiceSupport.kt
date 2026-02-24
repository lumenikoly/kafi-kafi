package com.lightkafka.core.kafka

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.Duration

internal suspend fun <T> runWithKafkaResult(
    operation: String,
    timeout: Duration,
    block: suspend () -> T,
): KafkaResult<T> =
    try {
        KafkaResult.Success(withTimeout(timeout.toMillis()) { block() })
    } catch (error: TimeoutCancellationException) {
        KafkaResult.Failure(
            KafkaServiceError.Timeout(
                operation = operation,
                timeout = timeout,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        KafkaResult.Failure(
            KafkaServiceError.OperationFailed(
                operation = operation,
                reason = error.message ?: "Unexpected Kafka operation failure",
                cause = error,
            ),
        )
    }
