package com.intellij.ide.starter.ide

import com.intellij.ide.starter.models.VMOptions
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.setPosixFilePermissions
import kotlin.io.path.writeText

/**
 * The script runs with a fake `xprop` and a fake `fluxbox` first on the `PATH`, so it needs no X server.
 * The fake `fluxbox` creates the marker, and the fake `xprop` reports a registered window manager while the marker exists.
 */
@DisabledOnOs(OS.WINDOWS)
class LinuxIdeDistributionTest {
  private companion object {
    /** A display on another host, so the script does not wait for a local Xvfb socket. */
    const val DISPLAY = "fake-host:5"
  }

  @TempDir
  lateinit var tempDir: Path

  private val marker: Path get() = tempDir.resolve("wm-registered")
  private val fluxboxPidFile: Path get() = tempDir.resolve("fluxbox.pid")

  @Test
  fun `a headless IDE gets no command line prefix`() {
    LinuxIdeDistribution.linuxCommandLine(xvfbRunLog(), vmOptions { inHeadlessMode() }).shouldBeEmpty()
  }

  @Test
  fun `an IDE on NoDisplay starts without xvfb-run`() {
    LinuxIdeDistribution.linuxCommandLine(xvfbRunLog(), vmOptions { withEnv("DISPLAY", "NoDisplay") }).take(2) shouldBe listOf("/bin/sh", "-c")
  }

  @Test
  fun `an IDE on a given display starts through the window manager script`() {
    val commandLine = LinuxIdeDistribution.linuxCommandLine(xvfbRunLog(), vmOptions { withEnv("DISPLAY", ":5") })

    commandLine.take(2) shouldBe listOf("/bin/sh", "-c")
    commandLine.drop(3) shouldBe listOf("sh", tempDir.resolve("fluxbox.log").toString())
  }

  @AfterEach
  fun stopFluxbox() {
    // without an X server, the fake fluxbox does not stop with the IDE
    if (fluxboxPidFile.exists()) ProcessHandle.of(fluxboxPidFile.readText().trim().toLong()).ifPresent { it.destroyForcibly() }
  }

  @Test
  fun `fluxbox starts before the IDE, and the IDE keeps the process of the start`() {
    val result = runScript(display = DISPLAY, ide = listOf("/bin/sh", "-c", "echo $$; [ -e '$marker' ] && exit 7"))

    result.exitCode shouldBe 7
    result.stdout.trim() shouldBe result.pid.toString()
    fluxboxPidFile.exists() shouldBe true
    result.stderr shouldContain "a window manager runs on $DISPLAY after about "
  }

  @Test
  fun `no fluxbox starts on a display without an X server`() {
    val result = runScript(display = "NoDisplay", ide = listOf("/bin/sh", "-c", "exit 0"))

    result.exitCode shouldBe 0
    result.stderr shouldBe ""
    fluxboxPidFile.exists() shouldBe false
  }

  @Test
  fun `no fluxbox starts when a window manager already runs`() {
    marker.writeText("")

    val result = runScript(display = DISPLAY, ide = listOf("/bin/sh", "-c", "exit 0"))

    result.exitCode shouldBe 0
    fluxboxPidFile.exists() shouldBe false
  }

  @Test
  fun `the IDE gets its arguments and its exit code passes through`() {
    val result = runScript(display = null, ide = listOf("/bin/sh", "-c", "echo \"$1|$2\"; exit 3", "sh", "a&b", "c d"))

    result.exitCode shouldBe 3
    result.stdout.trim() shouldBe "a&b|c d"
    fluxboxPidFile.exists() shouldBe false
  }

  @Test
  fun `the IDE starts also when fluxbox is not installed`() {
    val result = runScript(display = DISPLAY, ide = listOf("/bin/sh", "-c", "exit 0"), fakeTools = false)

    result.exitCode shouldBe 0
    result.stderr shouldContain "no window manager starts on $DISPLAY"
  }

  @Test
  fun `fluxbox starts again when it exits before it registers`() {
    val firstStart = tempDir.resolve("fluxbox-started")
    val fluxbox = "[ -e '$firstStart' ] || { touch '$firstStart'; exit 1; }; $registeringFluxbox"

    val result = runScript(display = DISPLAY, ide = listOf("/bin/sh", "-c", "[ -e '$marker' ] && exit 7"), fluxbox = fluxbox)

    result.exitCode shouldBe 7
    result.stderr shouldContain "attempt 1/3"
  }

  @Test
  fun `the fluxbox log names a window manager that exited three times, so the run reports it`() {
    val result = runScript(display = DISPLAY, ide = listOf("/bin/sh", "-c", "exit 0"), fluxbox = "exit 1")

    result.exitCode shouldBe 0
    result.stderr shouldContain "attempt 3/3"
    tempDir.resolve("fluxbox.log").readText() shouldContain "fluxbox is not the window manager on $DISPLAY, because fluxbox exited 3 times"
  }

  private class ScriptResult(val pid: Long, val exitCode: Int, val stdout: String, val stderr: String)

  private val registeringFluxbox: String get() = "echo $$ > '$fluxboxPidFile'; touch '$marker'; exec sleep 60"

  private fun runScript(display: String?, ide: List<String>, fakeTools: Boolean = true, fluxbox: String = registeringFluxbox): ScriptResult {
    val commandLine = LinuxIdeDistribution.linuxCommandLine(xvfbRunLog(), vmOptions { withEnv("DISPLAY", ":5") })
    val bin = tempDir.resolve("bin").createDirectories()
    if (fakeTools) {
      bin.writeScript("xprop", "[ -e '$marker' ] && echo '_NET_SUPPORTING_WM_CHECK(WINDOW): window id # 0x1'")
      bin.writeScript("fluxbox", fluxbox)
    }
    val process = ProcessBuilder(commandLine + ide).apply {
      environment().remove("DISPLAY")
      if (display != null) environment()["DISPLAY"] = display
      // without the fakes, only the empty directory and no system directory is on the PATH, so no real fluxbox is found
      environment()["PATH"] = if (fakeTools) "$bin:/usr/bin:/bin" else bin.toString()
    }.start()
    check(process.waitFor(30, TimeUnit.SECONDS)) { "The window manager script did not exit" }
    return ScriptResult(process.pid(), process.exitValue(), process.inputStream.bufferedReader().readText(), process.errorStream.bufferedReader().readText())
  }

  private fun Path.writeScript(name: String, body: String) {
    resolve(name).apply {
      writeText("#!/bin/sh\n$body\n")
      setPosixFilePermissions(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))
    }
  }

  private fun xvfbRunLog(): Path = tempDir.resolve("xvfb-log.txt")

  private fun vmOptions(configure: VMOptions.() -> Unit): VMOptions =
    VMOptions(ide = mock(InstalledIde::class.java), data = emptyList(), env = emptyMap()).apply(configure)
}
