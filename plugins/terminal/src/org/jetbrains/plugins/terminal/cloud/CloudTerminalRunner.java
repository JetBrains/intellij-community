// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.JBTerminalWidget;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.AbstractTerminalRunner;

import java.awt.*;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class CloudTerminalRunner extends AbstractTerminalRunner<CloudTerminalProcess> {
  private final String myPipeName;
  private final CloudTerminalProcess myProcess;

  public CloudTerminalRunner(@NotNull Project project, String pipeName, CloudTerminalProcess process) {
    super(project);
    myPipeName = pipeName;
    myProcess = process;
  }

  @NotNull
  @Override
  public JBTerminalWidget createTerminalWidget(@NotNull Disposable parent, @Nullable VirtualFile currentWorkingDirectory) {
    return super.createTerminalWidget(parent, currentWorkingDirectory, false);
  }

  @Override
  protected CloudTerminalProcess createProcess(@Nullable String directory) throws ExecutionException {
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
  protected String getTerminalConnectionName(CloudTerminalProcess process) {
    return "Terminal: " + myPipeName;
  }

  @Override
  protected TtyConnector createTtyConnector(CloudTerminalProcess process) {
    return new ProcessTtyConnector(process, Charset.defaultCharset()) {
      private Dimension myAppliedTermSize;

      @Override
      protected void resizeImmediately() {
        Dimension termSize = getPendingTermSize();
        if (Objects.equals(myAppliedTermSize, termSize)) {
          return;
        }
        if (termSize != null) {
          myProcess.resizeTty(termSize);
        }
        myAppliedTermSize = termSize;
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

  @Override
  public String runningTargetName() {
    return "Cloud terminal";
  }
}
