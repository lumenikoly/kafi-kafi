package com.lightkafka.ui.infra

import java.io.IOException

data class KraftLaunchResult(
    val success: Boolean,
    val message: String,
)

enum class KraftContainerState {
    RUNNING,
    STOPPED,
    NOT_FOUND,
    UNKNOWN,
}

data class KraftStatusResult(
    val success: Boolean,
    val state: KraftContainerState,
    val message: String,
)

class KraftLauncher(
    private val commandRunner: CommandRunner = ProcessCommandRunner(),
) {
    fun launch(): KraftLaunchResult {
        val availableEngine =
            detectEngine()
                ?: return KraftLaunchResult(
                    success = false,
                    message = "Neither podman nor docker is available",
                )

        val command =
            listOf(
                availableEngine,
                "run",
                "-d",
                "--name",
                "kafka-kraft",
                "-p",
                "9092:9092",
                "-e",
                "KAFKA_NODE_ID=1",
                "-e",
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,OUTSIDE:PLAINTEXT",
                "-e",
                "KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:29092,OUTSIDE://localhost:9092",
                "-e",
                "KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093,OUTSIDE://0.0.0.0:9092",
                "-e",
                "KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT",
                "-e",
                "KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093",
                "-e",
                "KAFKA_PROCESS_ROLES=broker,controller",
                "-e",
                "KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER",
                "-e",
                "KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1",
                "-e",
                "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS=0",
                "apache/kafka:4.1.1",
            )
        val runResult = runCommand(command)
        if (runResult.exitCode == 0) {
            return KraftLaunchResult(
                success = true,
                message = "Kafka KRaft started with $availableEngine",
            )
        }

        val conflictError = "already in use by container"
        if (runResult.stderr.contains(conflictError, ignoreCase = true)) {
            val startResult = runCommand(listOf(availableEngine, "start", "kafka-kraft"))
            if (startResult.exitCode == 0) {
                return KraftLaunchResult(
                    success = true,
                    message = "Kafka KRaft container was already created and is now started",
                )
            }
        }

        val errorText = runResult.stderr.ifBlank { runResult.stdout }.ifBlank { "Unknown error" }
        return KraftLaunchResult(
            success = false,
            message = "Failed to start Kafka KRaft via $availableEngine: $errorText",
        )
    }

    fun stop(): KraftLaunchResult {
        val availableEngine =
            detectEngine()
                ?: return KraftLaunchResult(
                    success = false,
                    message = "Neither podman nor docker is available",
                )

        val stopResult = runCommand(listOf(availableEngine, "stop", "kafka-kraft"))
        if (stopResult.exitCode == 0) {
            return KraftLaunchResult(
                success = true,
                message = "Kafka KRaft stopped with $availableEngine",
            )
        }

        val errorText = stopResult.stderr.ifBlank { stopResult.stdout }.ifBlank { "Unknown error" }
        val notFound = errorText.contains("No such", ignoreCase = true)
        return KraftLaunchResult(
            success = notFound,
            message =
                if (notFound) {
                    "Kafka KRaft container does not exist"
                } else {
                    "Failed to stop Kafka KRaft via $availableEngine: $errorText"
                },
        )
    }

    fun status(): KraftStatusResult {
        val availableEngine =
            detectEngine()
                ?: return KraftStatusResult(
                    success = false,
                    state = KraftContainerState.UNKNOWN,
                    message = "Neither podman nor docker is available",
                )

        val inspectResult =
            runCommand(
                listOf(availableEngine, "inspect", "-f", "{{.State.Running}}", "kafka-kraft"),
            )
        if (inspectResult.exitCode == 0) {
            val isRunning = inspectResult.stdout.trim().equals("true", ignoreCase = true)
            return if (isRunning) {
                KraftStatusResult(
                    success = true,
                    state = KraftContainerState.RUNNING,
                    message = "KRaft container is running",
                )
            } else {
                KraftStatusResult(
                    success = true,
                    state = KraftContainerState.STOPPED,
                    message = "KRaft container is stopped",
                )
            }
        }

        val errorText = inspectResult.stderr.ifBlank { inspectResult.stdout }.ifBlank { "Unknown error" }
        val notFound =
            errorText.contains("No such", ignoreCase = true) ||
                errorText.contains("not found", ignoreCase = true)
        return if (notFound) {
            KraftStatusResult(
                success = true,
                state = KraftContainerState.NOT_FOUND,
                message = "KRaft container is not created",
            )
        } else {
            KraftStatusResult(
                success = false,
                state = KraftContainerState.UNKNOWN,
                message = "Failed to get KRaft status via $availableEngine: $errorText",
            )
        }
    }

    private fun detectEngine(): String? {
        val engines = listOf("podman", "docker")
        return engines.firstOrNull { engine ->
            runCommand(listOf(engine, "--version")).exitCode == 0
        }
    }

    private fun runCommand(command: List<String>): CommandResult =
        try {
            commandRunner.run(command)
        } catch (error: IOException) {
            CommandResult(exitCode = -1, stdout = "", stderr = error.message ?: "Failed to run ${command.first()}")
        }
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

fun interface CommandRunner {
    fun run(command: List<String>): CommandResult
}

private class ProcessCommandRunner : CommandRunner {
    override fun run(command: List<String>): CommandResult {
        val process = ProcessBuilder(command).start()
        val stdout =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        val stderr =
            process.errorStream
                .bufferedReader()
                .readText()
                .trim()
        val exitCode = process.waitFor()
        return CommandResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
    }
}
