// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.base.Ascii;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalPanel;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.TerminalShellCommandHandler;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;

public class ShellTerminalWidget extends JBTerminalWidget {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);

  private final Project myProject;
  private boolean myEscapePressed = false;
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
      if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        myEscapePressed = true;
      }
      if (myPromptUpdateNeeded)  {
        myPrompt = getLineAtCursor();
        if (LOG.isDebugEnabled()) {
          LOG.info("Guessed shell prompt: " + myPrompt);
        }
        myPromptUpdateNeeded = false;
      }
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        handleShellCommandBeforeExecution(getTypedShellCommand(), e);
        myPromptUpdateNeeded = true;
        myEscapePressed = false;
      }
    });
  }

  private void handleShellCommandBeforeExecution(@NotNull String shellCommand, @NotNull KeyEvent enterEvent) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("typed shell command to execute: " + shellCommand);
    }
    if (executeShellCommandHandler(shellCommand)) {
      enterEvent.consume(); // do not send <ENTER> to shell
      TtyConnector connector = getTtyConnector();
      byte[] array = new byte[shellCommand.length()];
      Arrays.fill(array, Ascii.BS);
      try {
        connector.write(array);
      }
      catch (IOException e) {
        LOG.info("Cannot clear shell command " + shellCommand, e);
      }
    }
  }

  private boolean executeShellCommandHandler(@NotNull String command) {
    if (Experiments.getInstance().isFeatureEnabled("terminal.shell.command.handling")) {
      for (TerminalShellCommandHandler handler : TerminalShellCommandHandler.getEP().getExtensionList()) {
        if (handler.execute(myProject, command)) {
          return true;
        }
      }
    }
    return false;
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
    if (myPromptUpdateNeeded) {
      return "";
    }
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

  public void executeCommand(@NotNull String shellCommand) throws IOException {
    String typedCommand = getTypedShellCommand();
    if (!typedCommand.isEmpty()) {
      throw new IOException("Cannot execute command when another command is typed: " + typedCommand);
    }
    StringBuilder result = new StringBuilder();
    if (myEscapePressed) {
      result.append((char)KeyEvent.VK_BACK_SPACE); // remove Escape first, workaround for IDEA-221031
    }
    result.append(shellCommand).append('\n');
    getTtyConnector().write(result.toString());
  }

  public boolean hasRunningCommands() throws IllegalStateException {
    TtyConnector connector = getTtyConnector();
    if (connector instanceof ProcessTtyConnector) {
      return TerminalUtil.hasRunningCommands((ProcessTtyConnector)connector);
    }
    throw new IllegalStateException("Cannot determine if there are running processes for " + connector.getClass());
  }
}
