// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.google.common.base.Ascii;
import com.intellij.execution.ijent.IjentChildPtyProcessAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.terminal.pty.PtyProcessTtyConnector;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jediterm.core.util.TermSize;
import com.pty4j.PtyProcess;
import com.pty4j.unix.UnixPtyProcess;
import com.pty4j.windows.conpty.WinConPtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public class LocalTerminalTtyConnector extends PtyProcessTtyConnector {
  private static final Logger LOG = Logger.getInstance(LocalTerminalTtyConnector.class);
  private final @NotNull PtyProcess myProcess;
  private final @Nullable ShellProcessHolder myShellProcessHolder;

  public LocalTerminalTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset, @Nullable ShellProcessHolder shellProcessHolder) {
    super(process, charset);
    myProcess = process;
    myShellProcessHolder = shellProcessHolder;
  }

  @Override
  public void close() {
    if (myProcess instanceof UnixPtyProcess) {
      ((UnixPtyProcess)myProcess).hangup();
      AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
        if (myProcess.isAlive()) {
          LOG.info("Terminal hasn't been terminated by SIGHUP, performing default termination");
          myProcess.destroy();
        }
      }, 1000, TimeUnit.MILLISECONDS);
    }
    else if (myProcess instanceof IjentChildPtyProcessAdapter && myShellProcessHolder != null && myShellProcessHolder.isPosix()) {
      myShellProcessHolder.terminatePosixShell();
    }
    else {
      if (myProcess instanceof WinConPtyProcess winConPtyProcess && !winConPtyProcess.isBundledConPtyLibrary()) {
        sendInterruptToWinConPtyProcess();
      }
      myProcess.destroy();
    }
  }

  /**
   * Workaround for the ConPTY issue <a href="https://github.com/microsoft/terminal/issues/15373">#15373</a>
   * which was fixed in Windows Terminal 1.22.
   * Even though this version is bundled with IDE, ConPTY can be loaded from the OS.
   * @see <a href="https://github.com/microsoft/terminal/discussions/19030">workaround discussion</a>
   */
  private void sendInterruptToWinConPtyProcess() {
    OutputStream outputStream = myProcess.getOutputStream();
    if (outputStream != null && myProcess.isAlive()) {
      try {
        // ConPTY will process `0x03` and raise `CTRL_C_EVENT`.
        // This will hopefully terminate the whole process hierarchy of running user command,
        // which is a must for commands like `npm run dev`.
        outputStream.write(Ascii.ETX);
        outputStream.flush();
      }
      catch (IOException e) {
        LOG.info("Failed to send Ctrl+C to " + myProcess.getClass().getSimpleName() + ", alive:" + myProcess.isAlive(), e);
      }
    }
  }

  @Override
  public void resize(@NotNull TermSize termSize) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("resize to " + termSize);
    }
    super.resize(termSize);
  }
}
