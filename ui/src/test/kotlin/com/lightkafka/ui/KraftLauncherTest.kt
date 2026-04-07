package com.lightkafka.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KraftLauncherTest {
    @Test
    fun `launch uses podman when available`() {
        val launcher = KraftLauncher(commandRunner = FakeCommandRunner())

        val result = launcher.launch()

        assertTrue(result.success)
        assertTrue(result.message.contains("podman"))
    }

    @Test
    fun `launch falls back to docker when podman is unavailable`() {
        val launcher =
            KraftLauncher(
                commandRunner =
                    FakeCommandRunner(
                        podmanExists = false,
                        dockerExists = true,
                    ),
            )

        val result = launcher.launch()

        assertTrue(result.success)
        assertTrue(result.message.contains("docker"))
    }

    @Test
    fun `launch starts existing container on name conflict`() {
        val launcher =
            KraftLauncher(
                commandRunner =
                    FakeCommandRunner(
                        runExitCode = 125,
                        runStderr = "Error: container name is already in use by container abc",
                        startExitCode = 0,
                    ),
            )

        val result = launcher.launch()

        assertTrue(result.success)
        assertTrue(result.message.contains("already created"))
    }

    @Test
    fun `launch fails when no container runtime exists`() {
        val launcher =
            KraftLauncher(
                commandRunner =
                    FakeCommandRunner(
                        podmanExists = false,
                        dockerExists = false,
                    ),
            )

        val result = launcher.launch()

        assertFalse(result.success)
        assertTrue(result.message.contains("Neither podman nor docker"))
    }

    @Test
    fun `status returns running when container is active`() {
        val launcher = KraftLauncher(commandRunner = FakeCommandRunner(inspectStdout = "true"))

        val result = launcher.status()

        assertTrue(result.success)
        assertEquals(KraftContainerState.RUNNING, result.state)
    }

    @Test
    fun `status returns not found when container does not exist`() {
        val launcher =
            KraftLauncher(
                commandRunner =
                    FakeCommandRunner(
                        inspectExitCode = 1,
                        inspectStderr = "Error: No such object: kafka-kraft",
                    ),
            )

        val result = launcher.status()

        assertTrue(result.success)
        assertEquals(KraftContainerState.NOT_FOUND, result.state)
    }

    @Test
    fun `stop succeeds when container exists`() {
        val launcher = KraftLauncher(commandRunner = FakeCommandRunner(stopExitCode = 0))

        val result = launcher.stop()

        assertTrue(result.success)
        assertTrue(result.message.contains("stopped"))
    }

    @Test
    fun `stop is successful when container is absent`() {
        val launcher =
            KraftLauncher(
                commandRunner =
                    FakeCommandRunner(
                        stopExitCode = 1,
                        stopStderr = "Error: No such container: kafka-kraft",
                    ),
            )

        val result = launcher.stop()

        assertTrue(result.success)
        assertTrue(result.message.contains("does not exist"))
    }
}

private class FakeCommandRunner(
    private val podmanExists: Boolean = true,
    private val dockerExists: Boolean = true,
    private val runExitCode: Int = 0,
    private val runStdout: String = "container-id",
    private val runStderr: String = "",
    private val startExitCode: Int = 0,
    private val stopExitCode: Int = 0,
    private val stopStderr: String = "",
    private val inspectExitCode: Int = 0,
    private val inspectStdout: String = "false",
    private val inspectStderr: String = "",
) : CommandRunner {
    override fun run(command: List<String>): CommandResult {
        if (command.size >= 3 && command[0] == "sh" && command[1] == "-lc") {
            val probe = command[2]
            return when {
                probe.contains("podman") ->
                    if (podmanExists) CommandResult(0, "/usr/bin/podman", "") else CommandResult(1, "", "")

                probe.contains("docker") ->
                    if (dockerExists) CommandResult(0, "/usr/bin/docker", "") else CommandResult(1, "", "")

                else -> CommandResult(1, "", "")
            }
        }

        return when {
            command.drop(1).firstOrNull() == "run" ->
                CommandResult(exitCode = runExitCode, stdout = runStdout, stderr = runStderr)

            command.drop(1).firstOrNull() == "start" ->
                CommandResult(exitCode = startExitCode, stdout = "kafka-kraft", stderr = "")

            command.drop(1).firstOrNull() == "stop" ->
                CommandResult(exitCode = stopExitCode, stdout = "kafka-kraft", stderr = stopStderr)

            command.drop(1).firstOrNull() == "inspect" ->
                CommandResult(exitCode = inspectExitCode, stdout = inspectStdout, stderr = inspectStderr)

            else -> CommandResult(exitCode = 1, stdout = "", stderr = "unexpected command")
        }
    }
}
