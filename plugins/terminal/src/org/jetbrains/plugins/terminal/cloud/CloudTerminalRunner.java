// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.remoteServer.agent.util.log.TerminalListener.TtyResizeHandler;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.AbstractTerminalRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class CloudTerminalRunner extends AbstractTerminalRunner<CloudTerminalProcess> {
  private final @NlsSafe String myPipeName;
  private final CloudTerminalProcess myProcess;
  private final TtyResizeHandler myTtyResizeHandler;

  public CloudTerminalRunner(@NotNull Project project, @NotNull @NlsSafe String pipeName, @NotNull CloudTerminalProcess process,
                             @Nullable TtyResizeHandler resizeHandler) {
    super(project);
    myPipeName = pipeName;
    myProcess = process;
    myTtyResizeHandler = resizeHandler;
  }

  public CloudTerminalRunner(@NotNull Project project, @NotNull @NlsSafe String pipeName, CloudTerminalProcess process) {
    this(project, pipeName, process, null);
  }

  @Override
  public @NotNull CloudTerminalProcess createProcess(@NotNull ShellStartupOptions options) throws ExecutionException {
    return myProcess;
  }

  @Override
  public boolean isTerminalSessionPersistent() {
    return false;
  }

  @Override
  public @NotNull TtyConnector createTtyConnector(@NotNull CloudTerminalProcess process) {
    return new ProcessTtyConnector(process, StandardCharsets.UTF_8) {
      @Override
      public void resize(@NotNull TermSize termSize) {
        if (myTtyResizeHandler != null) {
          myTtyResizeHandler.onTtyResizeRequest(termSize.getColumns(), termSize.getRows());
        }
      }

      @Override
      public String getName() {
        return "Connector: " + myPipeName;
      }

      @Override
      public boolean isConnected() {
        return true;
      }
    };
  }

  @SuppressWarnings({"HardCodedStringLiteral", "DialogTitleCapitalization"})
  @Override
  public @NotNull String getDefaultTabTitle() {
    return "Cloud terminal";
  }
}
