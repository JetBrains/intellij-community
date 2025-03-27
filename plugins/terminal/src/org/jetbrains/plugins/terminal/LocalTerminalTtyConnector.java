// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.terminal.pty.PtyProcessTtyConnector;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jediterm.core.util.TermSize;
import com.pty4j.PtyProcess;
import com.pty4j.unix.UnixPtyProcess;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public class LocalTerminalTtyConnector extends PtyProcessTtyConnector {
  private static final Logger LOG = Logger.getInstance(LocalTerminalTtyConnector.class);
  private final @NotNull PtyProcess myProcess;

  LocalTerminalTtyConnector(@NotNull PtyProcess process, @NotNull Charset charset) {
    super(process, charset);
    myProcess = process;
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
    else {
      myProcess.destroy();
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
