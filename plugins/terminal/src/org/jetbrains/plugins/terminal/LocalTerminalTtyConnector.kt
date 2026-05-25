// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal

import com.google.common.base.Ascii
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.ijent.IjentChildPtyProcessAdapter
import com.intellij.terminal.pty.PtyProcessTtyConnector
import com.intellij.util.concurrency.AppExecutorUtil
import com.jediterm.core.util.TermSize
import com.pty4j.PtyProcess
import com.pty4j.unix.UnixPtyProcess
import com.pty4j.windows.conpty.WinConPtyProcess
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.util.ShellEelProcess
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

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
      process = ptyProcess
    )

  override fun close() {
    if (ptyProcess is UnixPtyProcess) {
      ptyProcess.hangup()
      AppExecutorUtil.getAppScheduledExecutorService().schedule(Runnable {
        if (ptyProcess.isAlive) {
          LOG.info("Terminal hasn't been terminated by SIGHUP, performing default termination")
          ptyProcess.destroy()
        }
      }, 1000, TimeUnit.MILLISECONDS)
    }
    else if (ptyProcess is IjentChildPtyProcessAdapter && shellProcessHolder.isPosix) {
      shellProcessHolder.terminatePosixShellBlocking()
    }
    else {
      if (ptyProcess is WinConPtyProcess && !ptyProcess.isBundledConPtyLibrary) {
        sendInterruptToWinConPtyProcess()
      }
      ptyProcess.destroy()
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

  override fun resize(termSize: TermSize) {
    LOG.debug { "resize to $termSize" }
    super.resize(termSize)
  }

  companion object {
    private val LOG = Logger.getInstance(LocalTerminalTtyConnector::class.java)
  }
}