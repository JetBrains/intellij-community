// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.remoteServer.agent.util.log.TerminalListener.TtyResizeHandler;
import com.intellij.terminal.ui.TerminalWidget;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.AbstractTerminalRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class CloudTerminalRunner extends AbstractTerminalRunner<CloudTerminalProcess> {
  private final @NlsSafe String myPipeName;
  private final CloudTerminalProcess myProcess;
  private final TtyResizeHandler myTtyResizeHandler;
  private final boolean myDeferSessionUntilFirstShown;

  public CloudTerminalRunner(@NotNull Project project, @NotNull @NlsSafe String pipeName, @NotNull CloudTerminalProcess process,
                             @Nullable TtyResizeHandler resizeHandler,
                             boolean deferSessionUntilFirstShown) {
    super(project);
    myPipeName = pipeName;
    myProcess = process;
    myTtyResizeHandler = resizeHandler;
    myDeferSessionUntilFirstShown = deferSessionUntilFirstShown;
  }

  public CloudTerminalRunner(@NotNull Project project, @NotNull @NlsSafe String pipeName, CloudTerminalProcess process) {
    this(project, pipeName, process, null, false);
  }

  @Override
  public @NotNull TerminalWidget startShellTerminalWidget(@NotNull Disposable parent,
                                                          @NotNull ShellStartupOptions startupOptions,
                                                          boolean deferSessionStartUntilUiShown) {
    return super.startShellTerminalWidget(parent, startupOptions, myDeferSessionUntilFirstShown);
  }

  @Override
  public @NotNull CloudTerminalProcess createProcess(@NotNull ShellStartupOptions options) throws ExecutionException {
    return myProcess;
  }

  @Override
  protected ProcessHandler createProcessHandler(final CloudTerminalProcess process) {
    return new ProcessHandler() {

      @Override
      protected void destroyProcessImpl() {
        process.destroy();
      }

      @Override
      protected void detachProcessImpl() {
        process.destroy();
      }

      @Override
      public boolean detachIsDefault() {
        return false;
      }

      @Nullable
      @Override
      public OutputStream getProcessInput() {
        return process.getOutputStream();
      }
    };
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
