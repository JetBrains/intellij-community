package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.junit5.helpers.SleepingApp
import com.intellij.ide.starter.process.ProcessInfo
import com.intellij.ide.starter.process.ProcessKiller
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.future.await
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
