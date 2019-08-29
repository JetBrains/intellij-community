// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalPanel;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.TerminalShellCommandHandler;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;

public class ShellTerminalWidget extends JBTerminalWidget {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);

  private final Project myProject;
  private String myCommandHistoryFilePath;
  private boolean myPromptUpdateNeeded = true;
  private String myPrompt = "";

  public ShellTerminalWidget(@NotNull Project project,
                             @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                             @NotNull Disposable parent) {
    super(project, settingsProvider, parent);
    myProject = project;
    ((JBTerminalPanel)getTerminalPanel()).addPreKeyEventHandler(e -> {
      if (e.getID() != KeyEvent.KEY_PRESSED) return;
      if (myPromptUpdateNeeded)  {
        myPrompt = getLineAtCursor();
        if (LOG.isDebugEnabled()) {
          LOG.info("Guessed shell prompt: " + myPrompt);
        }
        myPromptUpdateNeeded = false;
      }
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        fireShellCommandTyped(getTypedShellCommand());
        myPromptUpdateNeeded = true;
      }
    });
  }

  private void fireShellCommandTyped(@NotNull String command) {
    if (Experiments.getInstance().isFeatureEnabled("terminal.shell.command.handling")) {
      for (TerminalShellCommandHandler handler : TerminalShellCommandHandler.getEP().getExtensionList()) {
        if (handler.execute(myProject, command)) {
          break;
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      LOG.info("shell command typed: " + command);
    }
  }

  public void setCommandHistoryFilePath(@Nullable String commandHistoryFilePath) {
    myCommandHistoryFilePath = commandHistoryFilePath;
  }

  @Nullable
  public static String getCommandHistoryFilePath(@Nullable JBTerminalWidget terminalWidget) {
    return terminalWidget instanceof ShellTerminalWidget ? ((ShellTerminalWidget)terminalWidget).myCommandHistoryFilePath : null;
  }

  @NotNull
  public String getTypedShellCommand() {
    String line = getLineAtCursor();
    return StringUtil.trimStart(line, myPrompt);
  }

  @NotNull
  private String getLineAtCursor() {
    TerminalTextBuffer textBuffer = getTerminalPanel().getTerminalTextBuffer();
    Terminal terminal = getTerminal();
    int cursorY = Math.max(0, Math.min(terminal.getCursorY() - 1, textBuffer.getHeight() - 1));
    TerminalLine line = textBuffer.getLine(cursorY);
    if (line != null) {
      return line.getText();
    }
    return "";
  }
}
