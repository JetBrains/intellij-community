package com.intellij.ide.starter.utils

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.process.ProcessInfo
import com.intellij.ide.starter.process.ProcessKiller.killProcesses
import com.intellij.ide.starter.process.exec.ExecOutputRedirect
import com.intellij.ide.starter.process.exec.ProcessExecutor
import com.intellij.tools.ide.util.common.logOutput
import com.intellij.util.system.OS
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object PortUtil {

  fun isPortAvailable(host: InetAddress, port: Int): Boolean = checkFree(host, port, { true }) { false }

  fun getPortUnavailabilityReason(host: InetAddress, port: Int): String? = checkFree(host, port, { null }) {
    it.stackTraceToString()
  }

  private fun <T> checkFree(host: InetAddress, port: Int, onSuccess: () -> T, onFailure: (Throwable) -> T): T =
    try {
      ServerSocket().use {
        it.reuseAddress = true
        it.bind(InetSocketAddress(host, port), 50)
      }
      onSuccess()
    }
    catch (t: Throwable) {
      onFailure(t)
    }

  /**
   * Finds an available port starting from the proposed port on a specified host.
   * The separate error will be reported if the proposed port is not available.
   *
   * @throws IllegalStateException If no available ports are found within the specified range.
   */
  fun getAvailablePort(host: InetAddress = InetAddress.getLoopbackAddress(), proposedPort: Int): Int {
    if (isPortAvailable(host, proposedPort)) {
      return proposedPort
    }
    else {
      // TODO No doubt, it would be cleaner to propagate `suspend` into `applyVMOptionsPatch`, but it brings a lot of changes.
      //  Also, can't use `timeoutRunBlocking` here, it's not available in `intellij.tools.plugin.checker.tests`.
      val processes = runBlocking { getProcessesUsingPort(proposedPort) }

      val pidsInfoMap = processes?.associate { it.pid to it }
      val processNames = pidsInfoMap?.map { it.value.name }?.sorted()?.joinToString(", ")
                         ?: "Failed to retrieve processes"

      CIServer.instance.reportTestFailure(
        "Proposed port $proposedPort is not available on host $host as it used by processes ${processNames}",
        buildString {
          appendLine("Busy port could mean that the previous process is still running or the port is blocked by another application.")
          appendLine("Please make sure to investigate, the uninvestigated hanging processes could lead to further unclear test failure.")
          appendLine("PLEASE BE CAREFUL WHEN MUTING")

          if (pidsInfoMap != null) {
            appendLine()
            appendLine("Processes using the port $proposedPort:")
            pidsInfoMap.forEach { (_, info) -> appendLine(info.description) }
          }
        }, "")

      repeat(100) {
        if (isPortAvailable(host, proposedPort + it)) {
          return proposedPort + it
        }
      }

      error(buildString {
        appendLine("No available port found in a range $proposedPort..${proposedPort + 100}")
        listOf(proposedPort, proposedPort + 50, proposedPort + 100).forEach { port ->
          appendLine("Unavailability reason of $port is ${getPortUnavailabilityReason(host, port)}")
        }

        if (OS.CURRENT == OS.Windows) {
          runCatching {
            val (stdout, stderr) = findExcludedPortRanges()
            appendLine("Excluded port ranges:\n$stdout")
            if (stderr.isNotEmpty()) appendLine("Error message:\n$stderr")
          }
        }
      })
    }
  }

  /**
   * Retrieves a list of process IDs that are using a specific network port on the system.
   *
   * @param port The network port to check for processes.
   * @return A list of process IDs that are using the specified port, or null if an error occurs.
   */
  suspend fun getProcessesUsingPort(port: Int): List<ProcessInfo>? {
    var errorMsg = ""

    return runCatching {
      val findCommand = if (OS.CURRENT == OS.Windows) {
        listOf("cmd", "/c", "netstat -ano | findstr :$port")
      }
      else {
        listOf("sh", "-c", "lsof -i :$port -t")
      }

      val prefix = "find-pid"
      val stdoutRedirectFind = ExecOutputRedirect.ToStdOutAndString(prefix)
      val stderrRedirectFind = ExecOutputRedirect.ToStdOutAndString(prefix)

      ProcessExecutor(
        "Find Processes Using Port",
        workDir = null,
        stdoutRedirect = stdoutRedirectFind,
        stderrRedirect = stderrRedirectFind,
        args = findCommand,
        analyzeProcessExit = false
      ).startCancellable()

      val processIdsRaw = stdoutRedirectFind.read().trim()
      errorMsg = stderrRedirectFind.read()

      val pids: List<Int> = if (OS.CURRENT == OS.Windows) {
        processIdsRaw.split("\n").mapNotNull { line ->
          val tokens = line.removePrefix(prefix).trim().split("\\s+".toRegex())
          tokens.getOrNull(4)?.toIntOrNull()
        }
      }
      else {
        processIdsRaw.split("\n").mapNotNull { it.removePrefix(prefix).trim().toIntOrNull() }
      }

      coroutineScope {
        pids
          .map { pid ->
            async { ProcessInfo.create(pid.toLong(), portThatIsUsedByProcess = port) }
          }
          .awaitAll()
      }
    }.getOrElse {
      CIServer.instance.reportTestFailure(
        "An error occurred while attempting to get processes using port.",
        buildString {
          appendLine("An error occurred while attempting to get processes using port $port. ")
          if (errorMsg.isNotEmpty()) {
            appendLine("Error message: $errorMsg")
          }
          appendLine("Exception: ${it.stackTraceToString()}")
        }, "")
      return null
    }
  }

  suspend fun killProcessesUsingPort(port: Int): Boolean {
    val processes = getProcessesUsingPort(port)

    if (processes?.isNotEmpty() == true) {
      return killProcesses(processes)
    }
    else {
      if (processes == null) {
        CIServer.instance.reportTestFailure("Failed to retrieve processes using port", "Failed to retrieve processes using port $port", "")
      }
      else {
        CIServer.instance.reportTestFailure("No processes using port found", "No processes using port found $port", "")
      }
      return false
    }
  }

  private fun findExcludedPortRanges(): Pair<String, String> {
    val stdout = ExecOutputRedirect.ToStdOutAndString("[netsh]")
    val stderr = ExecOutputRedirect.ToStdOutAndString("[netsh]")
    ProcessExecutor(
      "find excluded port ranges",
      null,
      args = listOf("cmd", "/c", "netsh interface ipv4 show excludedportrange protocol=tcp"),
      stdoutRedirect = stdout,
      stderrRedirect = stderr,
      analyzeProcessExit = false
    ).start()
    return stdout.read() to stderr.read()
  }

  /**
   * Waits for the specified ports to be released (become available) on the given host.
   * This is useful after killing a process that was using these ports to ensure they are fully released
   * before attempting to bind to them again.
   *
   * @param host The host address to check (default: loopback)
   * @param ports The set of ports to wait for
   * @param timeout The maximum time to wait for all ports to be released (default: 10 seconds)
   * @param pollInterval The interval between port availability checks (default: 200 milliseconds)
   */
  suspend fun waitForPortsRelease(
    host: InetAddress = InetAddress.getLoopbackAddress(),
    ports: Set<Int>,
    timeout: Duration = 10.seconds,
    pollInterval: Duration = 200.milliseconds
  ) {
    if (ports.isEmpty()) return

    val startTime = System.currentTimeMillis()
    val timeoutMs = timeout.inWholeMilliseconds
    val busyPorts = ports.toMutableSet()

    logOutput("Waiting for ports to be released: ${ports.joinToString(", ")}")

    while (busyPorts.isNotEmpty()) {
      val elapsed = System.currentTimeMillis() - startTime
      if (elapsed >= timeoutMs) {
        logOutput("Timeout ($timeout) waiting for ports to be released. Still busy ports: ${busyPorts.joinToString(", ")}")
        break
      }

      val iterator = busyPorts.iterator()
      while (iterator.hasNext()) {
        val port = iterator.next()
        if (isPortAvailable(host, port)) {
          logOutput("Port $port is now available")
          iterator.remove()
        }
      }

      if (busyPorts.isNotEmpty()) {
        delay(pollInterval)
      }
    }

    if (busyPorts.isEmpty()) {
      logOutput("All ports have been released")
    }
  }
}
