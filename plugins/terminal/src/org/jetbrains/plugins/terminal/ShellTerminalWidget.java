// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.base.Ascii;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalPanel;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.TerminalShellCommandHandler;
import com.jediterm.terminal.*;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class ShellTerminalWidget extends JBTerminalWidget {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);

  private final Project myProject;
  private boolean myEscapePressed = false;
  private String myCommandHistoryFilePath;
  private boolean myPromptUpdateNeeded = true;
  private String myPrompt = "";
  private final Queue<String> myPendingCommandsToExecute = new LinkedList<>();

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
      if (e.getKeyCode() == KeyEvent.VK_ENTER && (e.getModifiers() & InputEvent.CTRL_MASK) != 0) {
        handleShellCommandBeforeExecution(getTypedShellCommand(), e);
        myPromptUpdateNeeded = true;
        myEscapePressed = false;
      }
    });

    JBTerminalPanel terminalPanel = (JBTerminalPanel)getTerminalPanel();
    terminalPanel.addPostProcessKeyEventHandler(e -> {
      String command = getTypedShellCommand();
      SubstringFinder.FindResult result =
        TerminalShellCommandHandler.Companion.isAvailable(project, command) ? searchMatchedCommand(command, true) : null;
      terminalPanel.setFindResult(result);
    });
  }

  @Nullable
  public SubstringFinder.FindResult searchMatchedCommand(@NotNull String pattern, boolean ignoreCase) {
    if (pattern.length() == 0) {
      return null;
    }

    final SubstringFinder finder = new SubstringFinder(pattern, ignoreCase);
    StyledTextConsumer consumer = new StyledTextConsumerAdapter() {
      @Override
      public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
        for (int i = 0; i < characters.length(); i++) {
          finder.nextChar(x, y - startRow, characters, i);
        }
      }
    };

    TerminalTextBuffer textBuffer = getTerminalTextBuffer();
    int currentLine = StringUtil.countNewLines(StringUtil.trimTrailing(textBuffer.getScreenLines()));
    if (currentLine != getLineNumberAtCursor()) {
      return null;
    }
    textBuffer.processScreenLines(currentLine, 1, consumer);

    return finder.getResult();
  }

  private void handleShellCommandBeforeExecution(@NotNull String shellCommand, @NotNull KeyEvent enterEvent) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("typed shell command to execute: " + shellCommand);
    }

    if (!TerminalShellCommandHandler.Companion.isAvailable(myProject, shellCommand)) {
      return;
    }

    TerminalShellCommandHandler.Companion
      .executeShellCommandHandler(myProject, shellCommand, () -> TerminalWorkingDirectoryManager.getWorkingDirectory(this, null));
    enterEvent.consume(); // do not send <CTRL ENTER> to shell
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
    TerminalLine line = textBuffer.getLine(getLineNumberAtCursor());
    if (line != null) {
      return line.getText();
    }
    return "";
  }

  private int getLineNumberAtCursor() {
    TerminalTextBuffer textBuffer = getTerminalPanel().getTerminalTextBuffer();
    Terminal terminal = getTerminal();
    return Math.max(0, Math.min(terminal.getCursorY() - 1, textBuffer.getHeight() - 1));
  }

  public void executeCommand(@NotNull String shellCommand) throws IOException {
    String typedCommand = getTypedShellCommand();
    if (!typedCommand.isEmpty()) {
      throw new IOException("Cannot execute command when another command is typed: " + typedCommand);
    }
    TtyConnector connector = getTtyConnector();
    if (connector != null) {
      doExecuteCommand(shellCommand, connector);
    }
    else {
      myPendingCommandsToExecute.add(shellCommand);
    }
  }

  @Override
  public void setTtyConnector(@NotNull TtyConnector ttyConnector) {
    super.setTtyConnector(ttyConnector);
    String command;
    while ((command = myPendingCommandsToExecute.poll()) != null) {
      try {
        doExecuteCommand(command, ttyConnector);
      }
      catch (IOException e) {
        LOG.warn("Cannot execute " + command, e);
      }
    }
  }

  private void doExecuteCommand(@NotNull String shellCommand, @NotNull TtyConnector connector) throws IOException {
    StringBuilder result = new StringBuilder();
    if (myEscapePressed) {
      result.append((char)KeyEvent.VK_BACK_SPACE); // remove Escape first, workaround for IDEA-221031
    }
    String enterCode = new String(getTerminalStarter().getCode(KeyEvent.VK_ENTER, 0), StandardCharsets.UTF_8);
    result.append(shellCommand).append(enterCode);
    connector.write(result.toString());
  }

  public boolean hasRunningCommands() throws IllegalStateException {
    TtyConnector connector = getTtyConnector();
    if (connector == null) return false;
    if (connector instanceof ProcessTtyConnector) {
      return TerminalUtil.hasRunningCommands((ProcessTtyConnector)connector);
    }
    throw new IllegalStateException("Cannot determine if there are running processes for " + connector.getClass());
  }
}
