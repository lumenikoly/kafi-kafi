package com.lightkafka.ui.infra

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
                        FakeResponses(
                            podmanExists = false,
                            dockerExists = true,
                        ),
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
                        FakeResponses(
                            run =
                                CommandResult(
                                    125,
                                    "container-id",
                                    "Error: container name is already in use by container abc",
                                ),
                            start = CommandResult(0, "kafka-kraft", ""),
                        ),
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
                        FakeResponses(
                            podmanExists = false,
                            dockerExists = false,
                        ),
                    ),
            )

        val result = launcher.launch()

        assertFalse(result.success)
        assertTrue(result.message.contains("Neither podman nor docker"))
    }

    @Test
    fun `status returns running when container is active`() {
        val launcher =
            KraftLauncher(
                commandRunner =
                    FakeCommandRunner(
                        FakeResponses(inspect = CommandResult(0, "true", "")),
                    ),
            )

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
                        FakeResponses(
                            inspect = CommandResult(1, "", "Error: No such object: kafka-kraft"),
                        ),
                    ),
            )

        val result = launcher.status()

        assertTrue(result.success)
        assertEquals(KraftContainerState.NOT_FOUND, result.state)
    }

    @Test
    fun `stop succeeds when container exists`() {
        val launcher = KraftLauncher(commandRunner = FakeCommandRunner())

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
                        FakeResponses(
                            stop = CommandResult(1, "kafka-kraft", "Error: No such container: kafka-kraft"),
                        ),
                    ),
            )

        val result = launcher.stop()

        assertTrue(result.success)
        assertTrue(result.message.contains("does not exist"))
    }
}

private data class FakeResponses(
    val podmanExists: Boolean = true,
    val dockerExists: Boolean = true,
    val run: CommandResult = CommandResult(0, "container-id", ""),
    val start: CommandResult = CommandResult(0, "kafka-kraft", ""),
    val stop: CommandResult = CommandResult(0, "kafka-kraft", ""),
    val inspect: CommandResult = CommandResult(0, "false", ""),
)

private class FakeCommandRunner(
    private val resp: FakeResponses = FakeResponses(),
) : CommandRunner {
    override fun run(command: List<String>): CommandResult {
        if (command.size >= 3 && command[0] == "sh" && command[1] == "-lc") {
            val probe = command[2]
            return when {
                probe.contains("podman") ->
                    if (resp.podmanExists) CommandResult(0, "/usr/bin/podman", "") else CommandResult(1, "", "")

                probe.contains("docker") ->
                    if (resp.dockerExists) CommandResult(0, "/usr/bin/docker", "") else CommandResult(1, "", "")

                else -> CommandResult(1, "", "")
            }
        }

        return when {
            command.drop(1).firstOrNull() == "run" -> resp.run
            command.drop(1).firstOrNull() == "start" -> resp.start
            command.drop(1).firstOrNull() == "stop" -> resp.stop
            command.drop(1).firstOrNull() == "inspect" -> resp.inspect
            else -> CommandResult(exitCode = 1, stdout = "", stderr = "unexpected command")
        }
    }
}
