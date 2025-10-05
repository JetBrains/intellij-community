// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal.ui;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.TtyConnector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalBundle;
import org.jetbrains.plugins.terminal.TerminalOptionsProvider;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import javax.swing.*;
import java.awt.*;

public final class TerminalContainer {
  @ApiStatus.Internal
  public static final @NotNull DataKey<TerminalWidget> TERMINAL_WIDGET_DATA_KEY = DataKey.create("terminalWidget");

  private final Content myContent;
  private final TerminalWidget myTerminalWidget;
  private final Project myProject;
  private final TerminalToolWindowManager myTerminalToolWindowManager;
  private @Nullable TerminalWrapperPanel myWrapperPanel;
  private boolean myForceHideUiWhenSessionEnds = false;

  public TerminalContainer(@NotNull Project project,
                           @NotNull Content content,
                           @NotNull TerminalWidget terminalWidget,
                           @NotNull TerminalToolWindowManager terminalToolWindowManager) {
    myProject = project;
    myContent = content;
    myTerminalWidget = terminalWidget;
    myTerminalToolWindowManager = terminalToolWindowManager;
    terminalWidget.addTerminationCallback(() -> {
      ApplicationManager.getApplication().invokeLater(() -> processSessionCompleted(), myProject.getDisposed());
    }, terminalWidget);
    terminalToolWindowManager.register(this);
    Disposer.register(content, () -> myTerminalToolWindowManager.unregister(this));
  }

  public @NotNull TerminalWidget getTerminalWidget() {
    return myTerminalWidget;
  }

  public @NotNull Content getContent() {
    return myContent;
  }

  public void closeAndHide() {
    myForceHideUiWhenSessionEnds = true;
    TtyConnector connector = myTerminalWidget.getTtyConnector();
    if (connector != null && connector.isConnected()) {
      connector.close();
    }
    else {
      // When "Close session when it ends" is off, terminal session is shown even with terminated process.
      processSessionCompleted();
    }
  }

  public @NotNull JPanel getWrapperPanel() {
    if (myWrapperPanel == null) {
      myWrapperPanel = new TerminalWrapperPanel(this);
    }
    return myWrapperPanel;
  }

  private void processSessionCompleted() {
    if (myForceHideUiWhenSessionEnds || TerminalOptionsProvider.getInstance().getCloseSessionOnLogout()) {
      myTerminalToolWindowManager.closeTab(myContent);
    }
    else {
      String text = getSessionCompletedMessage(myTerminalWidget);
      myTerminalWidget.writePlainMessage("\n" + text + "\n");
      myTerminalWidget.setCursorVisible(false);
    }
  }

  private static @NotNull @Nls String getSessionCompletedMessage(@NotNull TerminalWidget widget) {
    String text = "[" + TerminalBundle.message("session.terminated.text") + "]";
    ProcessTtyConnector connector = ShellTerminalWidget.getProcessTtyConnector(widget.getTtyConnector());
    if (connector != null) {
      Integer exitCode = null;
      try {
        exitCode = connector.getProcess().exitValue();
      }
      catch (IllegalThreadStateException ignored) {
      }
      return text + "\n[" + IdeCoreBundle.message("finished.with.exit.code.text.message", exitCode != null ? exitCode : "unknown") + "]";
    }
    return text;
  }

  private static final class TerminalWrapperPanel extends JPanel implements UiDataProvider {
    private TerminalContainer myTerminal;

    private TerminalWrapperPanel(@NotNull TerminalContainer terminal) {
      super(new BorderLayout());
      setBorder(null);
      setFocusable(false);
      setChildTerminal(terminal);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      if (myTerminal != null) {
        sink.set(TERMINAL_WIDGET_DATA_KEY, myTerminal.getTerminalWidget());
      }
    }

    private void setChildTerminal(@NotNull TerminalContainer terminal) {
      if (myTerminal != null) {
        throw new IllegalStateException("Cannot set a new terminal when another terminal is still set");
      }
      myTerminal = terminal;
      myTerminal.myWrapperPanel = this;
      setChildComponent(terminal.myTerminalWidget.getComponent());
    }

    private void setChildComponent(@NotNull Component childComponent) {
      Container parent = childComponent.getParent();
      if (parent != null) {
        parent.remove(childComponent);
      }
      removeAll();
      add(childComponent, BorderLayout.CENTER);
      revalidate();
    }
  }
}
