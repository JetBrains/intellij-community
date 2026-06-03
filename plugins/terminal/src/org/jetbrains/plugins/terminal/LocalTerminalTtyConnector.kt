// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.google.common.base.Ascii
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.ijent.IjentChildPtyProcessAdapter
import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.intellij.util.io.awaitExit
import com.jediterm.core.util.TermSize
import com.pty4j.PtyProcess
import com.pty4j.unix.UnixPtyProcess
import com.pty4j.windows.conpty.WinConPtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.util.ShellEelProcess
import org.jetbrains.plugins.terminal.util.terminalApplicationScope
import java.io.IOException
import java.nio.charset.Charset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class LocalTerminalTtyConnector internal constructor(
  private val shellProcessHolder: ShellProcessHolder,
  charset: Charset,
) : PtyProcessTtyConnector(shellProcessHolder.ptyProcess, charset) {
  private val ptyProcess: PtyProcess = shellProcessHolder.ptyProcess

  val eelDescriptor: EelDescriptor
    get() = shellProcessHolder.descriptor

  val shellEelProcess: ShellEelProcess
    get() = ShellEelProcess(
      eelProcess = shellProcessHolder.eelProcess,
      eelApi = shellProcessHolder.eelApi,
      ptyProcess = ptyProcess
    )

  /**
   * Closes `TtyConnector` asynchronously in the application-level scope.
   *
   * Note: the underlying child process may not have exited yet by the time the call returns.
   */
  override fun close() {
    terminalApplicationScope().launch(Dispatchers.IO) {
      closeSafely()
    }
  }

  /**
   * Terminates the underlying [ptyProcess] and awaits its completion for some meaningful time.
   * It is expected that process should exit before this function returns in most cases.
   *
   * Uses [NonCancellable] to avoid being canceled in the middle in case of IDE closing.
   */
  suspend fun closeSafely(): Unit = withContext(Dispatchers.IO + NonCancellable) {
    if (!ptyProcess.isAlive) return@withContext

    when {
      ptyProcess is UnixPtyProcess -> {
        terminateLocalPosixProcess(ptyProcess)
      }
      ptyProcess is IjentChildPtyProcessAdapter && shellProcessHolder.isPosix -> {
        terminateRemotePosixProcess(shellEelProcess)
      }
      else -> {
        if (ptyProcess is WinConPtyProcess && !ptyProcess.isBundledConPtyLibrary) {
          sendInterruptToWinConPtyProcess()
        }
        ptyProcess.destroy()
      }
    }

    val exitCode = ptyProcess.awaitExit(2.seconds)
    if (exitCode != null) {
      LOG.info("${processInfo(shellEelProcess)} has been terminated with exit code $exitCode")
    }
    else {
      LOG.warn("${processInfo(shellEelProcess)} has not been terminated!")
    }
  }

  private suspend fun terminateLocalPosixProcess(process: UnixPtyProcess) {
    process.hangup()
    if (process.awaitExit(1.seconds) == null) {
      LOG.info("Terminal hasn't been terminated by SIGHUP, performing default termination")
      process.destroy()
    }
  }

  private suspend fun terminateRemotePosixProcess(process: ShellEelProcess) {
    check(process.eelApi.platform.isPosix) { "Thin function is expected to be called only for posix process, but was: $process" }
    val ptyProcess = process.ptyProcess

    val shellPid = process.eelProcess.pid.value
    LOG.debug { "Sending SIGHUP to ${processInfo(process)}" }
    val killProcess = try {
      process.eelApi.exec.spawnProcess("kill").args("-HUP", shellPid.toString()).eelIt()
    }
    catch (e: ExecuteProcessException) {
      LOG.warn("Unable to send SIGHUP to ${processInfo(process)}", e)
      return
    }

    if (ptyProcess.awaitExit(5.seconds) == null) {
      val killProcessResult = withTimeoutOrNull(1.seconds) {
        killProcess.awaitProcessResult()
      }
      if (ptyProcess.isAlive) {
        LOG.info("${processInfo(process)} hasn't been terminated by SIGHUP, performing forceful termination. " +
                 "\"kill -HUP $shellPid\" => ${killProcessResult?.stringify()}")
        ptyProcess.destroyForcibly()
      }
    }
  }

  /**
   * Workaround for the ConPTY issue [#15373](https://github.com/microsoft/terminal/issues/15373)
   * which was fixed in Windows Terminal 1.22.
   * Even though this version is bundled with IDE, ConPTY can be loaded from the OS.
   *
   * See also [workaround discussion](https://github.com/microsoft/terminal/discussions/19030).
   */
  private fun sendInterruptToWinConPtyProcess() {
    val outputStream = ptyProcess.outputStream
    if (outputStream != null && ptyProcess.isAlive) {
      try {
        // ConPTY will process `0x03` and raise `CTRL_C_EVENT`.
        // This will hopefully terminate the whole process hierarchy of running user command,
        // which is a must for commands like `npm run dev`.
        outputStream.write(Ascii.ETX.toInt())
        outputStream.flush()
      }
      catch (e: IOException) {
        LOG.info("Failed to send Ctrl+C to ${ptyProcess.javaClass.getSimpleName()}, alive:${ptyProcess.isAlive}", e)
      }
    }
  }

  /**
   * @return The exit value of the process if it exits within the timeout or null otherwise.
   */
  private suspend fun Process.awaitExit(timeout: Duration): Int? {
    return withTimeoutOrNull(timeout) {
      this@awaitExit.awaitExit()
    }
  }

  private fun processInfo(process: ShellEelProcess): String {
    return "${process.ptyProcess::class.java.name}(${process.eelProcess.pid.value})"
  }

  private fun EelProcessExecutionResult.stringify(): String {
    return "(exitCode=$exitCode, stdout=$stdoutString, stderr=$stderrString)"
  }

  override fun resize(termSize: TermSize) {
    LOG.debug { "resize to $termSize" }
    super.resize(termSize)
  }

  companion object {
    private val LOG = Logger.getInstance(LocalTerminalTtyConnector::class.java)
  }
}