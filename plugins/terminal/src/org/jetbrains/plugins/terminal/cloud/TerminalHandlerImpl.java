// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.agent.util.log.TerminalListener.TtyResizeHandler;
import com.intellij.remoteServer.impl.runtime.log.TerminalHandlerBase;
import com.jediterm.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.InputStream;
import java.io.OutputStream;

public class TerminalHandlerImpl extends TerminalHandlerBase {

  private final TerminalWidget myTerminalWidget;

  public TerminalHandlerImpl(@NotNull String presentableName,
                             @NotNull Project project,
                             @NotNull InputStream terminalOutput,
                             @NotNull OutputStream terminalInput,
                             boolean deferTerminalSessionUntilFirstShown) {
    super(presentableName);

    final CloudTerminalProcess process = new CloudTerminalProcess(terminalInput, terminalOutput);

    TtyResizeHandler handlerBoundLater = (w, h) -> getResizeHandler().onTtyResizeRequest(w, h); //right now handler is null
    CloudTerminalRunner terminalRunner =
      new CloudTerminalRunner(project, presentableName, process, handlerBoundLater, deferTerminalSessionUntilFirstShown);

    myTerminalWidget = terminalRunner.createTerminalWidget(project, null);
  }

  @Override
  public JComponent getComponent() {
    return myTerminalWidget.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTerminalWidget.getPreferredFocusableComponent();
  }
}
