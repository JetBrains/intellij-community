package com.intellij.ide.starter.junit5

import com.intellij.ide.starter.junit5.helpers.ConnectingSocketApp
import com.intellij.ide.starter.junit5.helpers.ListeningSocketApp
import com.intellij.ide.starter.utils.PortUtil
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Paths

class PortUtilTest {

  private fun findFreePort(): Int = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }

  private fun currentPid(): Long = ProcessHandle.current().pid()

  private fun getJavaBin(): String {
    val javaHome = System.getProperty("java.home")
    val javaExe = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
    val candidate = Paths.get(javaHome, "bin", javaExe).toFile()
    return if (candidate.isFile) candidate.absolutePath else "java"
  }

  private fun startListeningProcess(port: Int, attempt: Int = 0): Process {
    return startProcessUsingPort(port, Mode.LISTENING, attempt)
  }

  private fun startConnectingProcess(port: Int, attempt: Int = 0): Process {
    return startProcessUsingPort(port, Mode.CONNECTING, attempt)
  }

  private enum class Mode {
    LISTENING, CONNECTING
  }

  private fun startProcessUsingPort(port: Int, mode: Mode, attempt: Int = 0): Process {
    if (attempt > 5) {
      error("Failed to connect to port $port after $attempt attempts")
    }
    val neededApp = if (mode == Mode.CONNECTING) {
      ConnectingSocketApp::class.java.name
    }
    else {
      ListeningSocketApp::class.java.name
    }

    val classPath = System.getProperty("java.class.path")
    val classpathFile = Files.createTempFile("", "classpath-${System.currentTimeMillis()}.txt")
    Files.writeString(classpathFile, classPath)

    val pb = ProcessBuilder(listOf(getJavaBin(), "-cp", "@${classpathFile.toAbsolutePath()}", neededApp, port.toString()))

    pb.inheritIO()
    val process = pb.start()
    Thread.sleep(300)
    if (!process.isAlive) {
      return startConnectingProcess(port, attempt + 1)
    }
    return process
  }

  @Test
  fun getProcessesUsingPort_returnsCurrentProcessPid_whenCurrentProcessListensOnPort() {
    val port = findFreePort()
    val server = ServerSocket(port, 0, InetAddress.getLoopbackAddress())
    server.use { _ ->
      Thread.sleep(1000)

      val processes = PortUtil.getProcessesUsingPort(port) ?: error("Expected non-null list of processes using port $port")
      val pids = processes.map { it.pid.toInt() }
      assertTrue(pids.contains(currentPid().toInt()), "Expected current JVM PID to be reported for listening port $port")
    }
  }

  @Test
  fun killProcessesUsingPort_killsChildProcessListeningOnPort() {
    val port = findFreePort()
    val child = startListeningProcess(port)
    val childPid = child.pid().toInt()
    try {
      val processes = PortUtil.getProcessesUsingPort(port) ?: error("Expected non-null list of processes using port $port")
      val pids = processes.map { it.pid.toInt() }
      assertTrue(pids.contains(childPid), "Expected child PID $childPid to be listed for port $port, got $pids")

      val killed = PortUtil.killProcessesUsingPort(port)
      assertTrue(killed, "Expected killProcessesUsingPort($port) to finish successfully")

      // Wait for process to terminate
      val exited = child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(exited, "Child process did not exit after kill")
    }
    finally {
      // Ensure cleanup if kill failed
      child.destroyForcibly()
    }
  }

  @Test
  fun getProcessesUsingPort_returnsMultiplePids_whenTwoClientsConnected() {
    val port = findFreePort()
    ServerSocket(port, 0, InetAddress.getLoopbackAddress()).use {
      val c1 = startConnectingProcess(port)
      val c2 = startConnectingProcess(port)
      val pid1 = c1.pid().toInt()
      val pid2 = c2.pid().toInt()
      try {
        val processes = PortUtil.getProcessesUsingPort(port) ?: error("Expected non-null list of processes using port $port")
        val pids = processes.map { it.pid.toInt() }

        assertTrue(pids.contains(currentPid().toInt()), "Expected current JVM PID to be reported for listening port $port")
        assertTrue(pids.contains(pid1), "Expected first client PID $pid1 to be listed for port $port, got $pids")
        assertTrue(pids.contains(pid2), "Expected second client PID $pid2 to be listed for port $port, got $pids")
      }
      finally {
        c1.destroyForcibly()
        c2.destroyForcibly()
      }
    }
  }

  @Test
  fun killProcessesUsingPort_killsAllChildProcesses_whenListenerAndTwoClientsUsePort() {
    val port = findFreePort()
    val listener = startListeningProcess(port)
    val client1 = startConnectingProcess(port)
    val client2 = startConnectingProcess(port)
    val listenerPid = listener.pid().toInt()
    val pid1 = client1.pid().toInt()
    val pid2 = client2.pid().toInt()
    try {
      val processes = PortUtil.getProcessesUsingPort(port) ?: error("Expected non-null list of processes using port $port")
      val pids = processes.map { it.pid.toInt() }
      assertTrue(pids.contains(listenerPid), "Expected listener PID $listenerPid to be listed for port $port, got $pids")
      assertTrue(pids.contains(pid1), "Expected first client PID $pid1 to be listed for port $port, got $pids")
      assertTrue(pids.contains(pid2), "Expected second client PID $pid2 to be listed for port $port, got $pids")

      val killed = PortUtil.killProcessesUsingPort(port)
      assertTrue(killed, "Expected killProcessesUsingPort($port) to finish successfully")

      val exitedListener = listener.waitFor(7, java.util.concurrent.TimeUnit.SECONDS)
      val exited1 = client1.waitFor(7, java.util.concurrent.TimeUnit.SECONDS)
      val exited2 = client2.waitFor(7, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(exitedListener && exited1 && exited2, "Not all child processes exited after kill: listener=$exitedListener c1=$exited1 c2=$exited2")
    }
    finally {
      listener.destroyForcibly()
      client1.destroyForcibly()
      client2.destroyForcibly()
    }
  }
}