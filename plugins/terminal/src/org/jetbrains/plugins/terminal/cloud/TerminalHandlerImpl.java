// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.cloud;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.agent.util.log.TerminalListener.TtyResizeHandler;
import com.intellij.remoteServer.impl.runtime.log.TerminalHandlerBase;
import com.intellij.terminal.JBTerminalPanel;
import com.intellij.terminal.JBTerminalWidget;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.io.OutputStream;

public class TerminalHandlerImpl extends TerminalHandlerBase {

  private final JBTerminalWidget myTerminalWidget;
  private final JBTerminalPanel myTerminalPanel;
  private final KeyAdapter myCopyActionKeyListener;

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
    myTerminalPanel = myTerminalWidget.getTerminalPanel();
    myCopyActionKeyListener = new MyCopyActionKeyListener();
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
  public void dispose() {
    super.dispose();
    myTerminalPanel.removeCustomKeyListener(myCopyActionKeyListener);
  }

  @Override
  public void close() {
    myTerminalWidget.getTerminalDisplay().setCursorVisible(false);
    myTerminalWidget.stop();
    // workaround for unexpected removing key listener from terminal panel, even if panel not disposed
    myTerminalPanel.addCustomKeyListener(myCopyActionKeyListener);
    super.close();
  }

  private class MyCopyActionKeyListener extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent e) {
      var copyActionName = myTerminalWidget.getSettingsProvider().getCopyActionPresentation().getName();

      myTerminalPanel.getActions().stream().filter(a -> a.getName().equals(copyActionName)).findFirst().ifPresent(a -> {
        if (a.matches(e) && a.isEnabled(e)) {
          a.actionPerformed(e);
        }
      });
    }
  }
}
