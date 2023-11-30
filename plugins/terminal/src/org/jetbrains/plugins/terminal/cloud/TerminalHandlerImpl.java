// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.agent.util.log.TerminalListener.TtyResizeHandler;
import com.intellij.remoteServer.impl.runtime.log.TerminalHandlerBase;
import com.intellij.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.ShellStartupOptions;

import javax.swing.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

public final class TerminalHandlerImpl extends TerminalHandlerBase {

  private final TerminalWidget myTerminalWidget;

  /**
   * @deprecated use {@link TerminalHandlerImpl#TerminalHandlerImpl(String, Project, InputStream, OutputStream)} instead
   */
  @Deprecated(forRemoval = true)
  public TerminalHandlerImpl(@NotNull String presentableName,
                             @NotNull Project project,
                             @NotNull InputStream terminalOutput,
                             @NotNull OutputStream terminalInput,
                             @SuppressWarnings("unused") boolean deferTerminalSessionUntilFirstShown) {
    this(presentableName, project, terminalOutput, terminalInput);
  }

  public TerminalHandlerImpl(@NotNull String presentableName,
                             @NotNull Project project,
                             @NotNull InputStream terminalOutput,
                             @NotNull OutputStream terminalInput) {
    super(presentableName);

    final CloudTerminalProcess process = new CloudTerminalProcess(terminalInput, terminalOutput);

    TtyResizeHandler handlerBoundLater = (w, h) -> getResizeHandler().onTtyResizeRequest(w, h); //right now handler is null
    CloudTerminalRunner terminalRunner =
      new CloudTerminalRunner(project, presentableName, process, handlerBoundLater);

    myTerminalWidget = terminalRunner.startShellTerminalWidget(this, new ShellStartupOptions.Builder().build(), true);
  }

  @Override
  public JComponent getComponent() {
    return myTerminalWidget.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTerminalWidget.getPreferredFocusableComponent();
  }

  @Override
  public void close() {
    myTerminalWidget.setCursorVisible(false);
    Objects.requireNonNull(myTerminalWidget.getTtyConnector()).close();
    super.close();
  }
}
