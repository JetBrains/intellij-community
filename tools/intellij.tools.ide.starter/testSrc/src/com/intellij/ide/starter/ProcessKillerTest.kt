package com.intellij.ide.starter

import com.intellij.ide.starter.helpers.SleepingApp
import com.intellij.ide.starter.process.ProcessInfo
import com.intellij.ide.starter.process.ProcessKiller
import com.intellij.testFramework.common.timeoutRunBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class ProcessKillerTest {

  private fun getJavaBin(): String {
    val javaHome = System.getProperty("java.home")
    val javaExe = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
    val candidate = Paths.get(javaHome, "bin", javaExe).toFile()
    return if (candidate.isFile) candidate.absolutePath else "java"
  }

  private fun spawnSleepingProcess(): Process {
    val classPath = System.getProperty("java.class.path")
    val classpathFile = Files.createTempFile("", "classpath-${System.currentTimeMillis()}.txt")
    val escaped = classPath.replace("\\", "\\\\").replace("\"", "\\\"")
    Files.writeString(classpathFile, "\"$escaped\"")
    val process = ProcessBuilder(getJavaBin(), "-cp", "@${classpathFile.toAbsolutePath()}", SleepingApp::class.java.name)
      .inheritIO()
      .start()
    Thread.sleep(300)
    check(process.isAlive) { "Sleeping process failed to start" }
    return process
  }

  // ── killProcess(Process) ────────────────────────────────────────────────────

  @Test
  fun killProcess_Process_killsLiveProcess(): Unit = timeoutRunBlocking {
    val process = spawnSleepingProcess()
    try {
      val result = ProcessKiller.killProcess(process, gracefulTimeout = 3.seconds, forcefulTimeout = 3.seconds)
      assertTrue(result, "killProcess should return true")
      assertFalse(process.isAlive, "Process should be dead after kill")
    }
    finally {
      process.destroyForcibly()
    }
  }

  @Test
  fun killProcess_Process_returnsTrueForAlreadyDeadProcess(): Unit = timeoutRunBlocking {
    val process = spawnSleepingProcess()
    process.destroyForcibly()
    check(process.waitFor(2, TimeUnit.SECONDS)) { "Process did not exit after destroyForcibly()" }
    val result = ProcessKiller.killProcess(process, gracefulTimeout = 1.seconds, forcefulTimeout = 1.seconds)
    assertTrue(result, "killProcess should return true for a process that is already dead")
  }

  @Test
  fun killProcess_Process_withGracefullyAtFirstFalse_stillKills(): Unit = timeoutRunBlocking {
    val process = spawnSleepingProcess()
    try {
      val result = ProcessKiller.killProcess(process, gracefullyAtFirst = false, forcefulTimeout = 3.seconds)
      assertTrue(result, "killProcess should return true")
      assertFalse(process.isAlive, "Process should be dead after forceful-only kill")
    }
    finally {
      process.destroyForcibly()
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun killProcess_Process_letsDescendantFinishShutdownWithinGracefulTimeout(): Unit = timeoutRunBlocking {
    // The child spends 3 s in its SIGTERM handler, longer than the 2 s that `gracefulStop` waits for a descendant.
    val dir = Files.createTempDirectory("process-killer")
    val marker = dir.resolve("finished")
    val child = dir.resolve("child.sh")
    Files.writeString(child, "trap 'sleep 3; touch \"$1\"; exit 0' TERM\nwhile :; do sleep 0.1; done\n")
    val wrapper = ProcessBuilder("sh", "-c", "sh \"$0\" \"$1\" & wait", child.toString(), marker.toString()).start()
    // the loop runs only after the trap is set
    waitForSleepDescendant(wrapper)
    try {
      val result = ProcessKiller.killProcess(wrapper, gracefulTimeout = 10.seconds, forcefulTimeout = 3.seconds)
      assertTrue(result, "killProcess should return true")
      assertTrue(Files.exists(marker), "The child should finish its SIGTERM handler instead of being killed")
    }
    finally {
      wrapper.destroyForcibly()
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun killProcess_Process_killsDescendantThatOutlivesWrapper(): Unit = timeoutRunBlocking {
    // The wrapper exits on SIGTERM; the child ignores it, as a JVM stuck at a safepoint does.
    val wrapper = ProcessBuilder("sh", "-c", "sh -c \"trap '' TERM; exec sleep 600\" & wait").start()
    val child = waitForSleepDescendant(wrapper)
    try {
      val result = ProcessKiller.killProcess(wrapper, gracefulTimeout = 1.seconds, forcefulTimeout = 3.seconds)
      assertTrue(result, "killProcess should return true")
      assertFalse(wrapper.isAlive, "Wrapper should be dead after kill")
      assertFalse(child.isAlive, "Descendant ignoring SIGTERM should be dead after kill")
    }
    finally {
      child.destroyForcibly()
      wrapper.destroyForcibly()
    }
  }

  /**
   * The first `sleep` descendant of [process]. A script starts it only after it has set its SIGTERM trap.
   */
  private fun waitForSleepDescendant(process: Process): ProcessHandle {
    repeat(50) {
      val sleep = process.toHandle().descendants()
        .filter { it.info().command().orElse("").endsWith("sleep") }
        .findFirst().orElse(null)
      if (sleep != null) return sleep
      Thread.sleep(100)
    }
    error("Process ${process.pid()} did not start `sleep`")
  }

  // ── killProcesses ───────────────────────────────────────────────────────────

  @Test
  fun killProcesses_throwsOnEmptyList(): Unit = timeoutRunBlocking {
    var threw = false
    try {
      ProcessKiller.killProcesses(emptyList())
    }
    catch (_: IllegalStateException) {
      threw = true
    }
    assertTrue(threw, "killProcesses should throw IllegalStateException on an empty list")
  }

  @Test
  fun killProcesses_killsAllProcesses(): Unit = timeoutRunBlocking {
    val p1 = spawnSleepingProcess()
    val p2 = spawnSleepingProcess()
    try {
      val infos = listOf(ProcessInfo.create(p1.pid()), ProcessInfo.create(p2.pid()))
      val result = ProcessKiller.killProcesses(infos, gracefulTimeout = 3.seconds, forcefulTimeout = 3.seconds)
      assertTrue(result, "killProcesses should return true")
      assertFalse(p1.isAlive, "First process should be dead")
      assertFalse(p2.isAlive, "Second process should be dead")
    }
    finally {
      p1.destroyForcibly()
      p2.destroyForcibly()
    }
  }
}
