// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.base.Ascii;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalPanel;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.TerminalShellCommandHandler;
import com.intellij.util.Alarm;
import com.jediterm.terminal.*;
import com.jediterm.terminal.model.CharBuffer;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;

import javax.swing.event.HyperlinkEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class ShellTerminalWidget extends JBTerminalWidget {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);

  @NonNls private static final String TERMINAL_CUSTOM_COMMANDS_GOT_IT = "TERMINAL_CUSTOM_COMMANDS_GOT_IT";
  @NonNls private static final String GOT_IT = "got_it";

  private final Project myProject;
  private boolean myEscapePressed = false;
  private String myCommandHistoryFilePath;
  private boolean myPromptUpdateNeeded = true;
  private String myPrompt = "";
  private final Queue<String> myPendingCommandsToExecute = new LinkedList<>();
  @Nullable private String myWorkingDirectory;

  public ShellTerminalWidget(@NotNull Project project,
                             @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                             @NotNull Disposable parent) {
    super(project, settingsProvider, parent);
    myProject = project;
    myWorkingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(this, null);

    Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    ((JBTerminalPanel)getTerminalPanel()).addPreKeyEventHandler(e -> {
      if (e.getID() != KeyEvent.KEY_PRESSED) return;
      if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        myEscapePressed = true;
      }
      if (myPromptUpdateNeeded) {
        myPrompt = getLineAtCursor();
        myWorkingDirectory = TerminalWorkingDirectoryManager.getWorkingDirectory(this, null);
        if (LOG.isDebugEnabled()) {
          LOG.info("Guessed shell prompt: " + myPrompt);
        }
        myPromptUpdateNeeded = false;
      }

      alarm.cancelAllRequests();
      alarm.addRequest(() -> {
        highlightMatchedCommand(project);
      }, 50);

      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
        if ((e.getModifiers() & InputEvent.CTRL_MASK) != 0) {
          executeMatchedCommand(getTypedShellCommand(), e);
        }
        if (!e.isConsumed()) {
          myPromptUpdateNeeded = true;
          myEscapePressed = false;
        }
      }
    });
  }

  private void highlightMatchedCommand(@NotNull Project project) {
    if (!PropertiesComponent.getInstance(project).getBoolean(TerminalCommandHandlerCustomizer.TERMINAL_CUSTOM_COMMAND_EXECUTION, true)) {
      getTerminalPanel().setFindResult(null);
      return;
    }

    //highlight matched command
    String command = getTypedShellCommand();
    SubstringFinder.FindResult result =
      TerminalShellCommandHandler.Companion.matches(project, myWorkingDirectory, !hasRunningCommands(), command)
      ? searchMatchedCommand(command, true) : null;
    getTerminalPanel().setFindResult(result);

    //show notification
    if (PropertiesComponent.getInstance(project).getBoolean(TERMINAL_CUSTOM_COMMANDS_GOT_IT, false)) {
      return;
    }

    if (result != null) {
      String content =
        "Highlighted commands can be interpreted and executed by the IDE in a smart way.<br>" +
        "Press <b>Ctrl+Enter</b> to try this, or <b>Enter</b> to run the command in the console as usual.<br>" +
        "You can turn this behavior on/off in Preferences | Tools | Terminal. <a href=\"" + GOT_IT + "\"/>Got it!</a>";

      new SingletonNotificationManager(
        NotificationGroup.toolWindowGroup("Terminal", TerminalToolWindowFactory.TOOL_WINDOW_ID), NotificationType.INFORMATION, null)
        .notify("Smart commands execution", content, project,
                new NotificationListener.Adapter() {
                  @Override
                  protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
                    if (GOT_IT.equals(e.getDescription())) {
                      PropertiesComponent.getInstance(project)
                        .setValue(TERMINAL_CUSTOM_COMMANDS_GOT_IT, true, false);
                    }
                  }
                });
    }
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

  private void executeMatchedCommand(@NotNull String command, @NotNull KeyEvent enterEvent) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("typed shell command to execute: " + command);
    }

    if (!TerminalShellCommandHandler.Companion.matches(myProject, myWorkingDirectory, !hasRunningCommands(), command)) {
      return;
    }

    TerminalShellCommandHandler.Companion.executeShellCommandHandler(myProject, myWorkingDirectory, !hasRunningCommands(), command);
    enterEvent.consume(); // do not send <CTRL ENTER> to shell
    TtyConnector connector = getTtyConnector();
    byte[] array = new byte[command.length()];
    Arrays.fill(array, Ascii.BS);
    try {
      connector.write(array);
    }
    catch (IOException e) {
      LOG.info("Cannot clear shell command " + command, e);
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
