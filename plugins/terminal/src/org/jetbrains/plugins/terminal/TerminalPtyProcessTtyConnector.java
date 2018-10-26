// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.jediterm.pty.PtyProcessTtyConnector;
import com.pty4j.PtyProcess;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

public class TerminalPtyProcessTtyConnector extends PtyProcessTtyConnector {
  private final PtyProcess myProcess;

  public TerminalPtyProcessTtyConnector(@NotNull PtyProcess process, Charset charset) {
    super(process, charset);
    myProcess = process;
  }

  // TODO move to com.jediterm.pty.PtyProcessTtyConnector
  @NotNull
  public PtyProcess getProcess() {
    return myProcess;
  }
}
