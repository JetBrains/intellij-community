// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalPanel;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.actions.TerminalActionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalAction;
import com.pty4j.unix.UnixPtyProcess;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.RenameTerminalSessionActionKt;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class ShellTerminalWidget extends JBTerminalWidget {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);

  private final Project myProject;
  private boolean myEscapePressed = false;
  private String myCommandHistoryFilePath;
  private boolean myPromptUpdateNeeded = true;
  private String myPrompt = "";
  private final Queue<String> myPendingCommandsToExecute = new LinkedList<>();
  private final TerminalShellCommandHandlerHelper myShellCommandHandlerHelper;

  public ShellTerminalWidget(@NotNull Project project,
                             @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                             @NotNull Disposable parent) {
    super(project, settingsProvider, parent);
    myProject = project;
    myShellCommandHandlerHelper = new TerminalShellCommandHandlerHelper(this);

    ((JBTerminalPanel)getTerminalPanel()).addPreKeyEventHandler(e -> {
      if (e.getID() != KeyEvent.KEY_PRESSED) return;
      if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        myEscapePressed = true;
      }
      if (myPromptUpdateNeeded) {
        myPrompt = getLineAtCursor();
        if (LOG.isDebugEnabled()) {
          LOG.info("Guessed shell prompt: " + myPrompt);
        }
        myPromptUpdateNeeded = false;
      }

      if (e.getKeyCode() == KeyEvent.VK_ENTER || TerminalShellCommandHandlerHelper.matchedExecutor(e) != null) {
        TerminalUsageTriggerCollector.Companion.triggerCommandExecuted(myProject);
        if (myShellCommandHandlerHelper.processEnterKeyPressed(e)) {
          e.consume();
        }
        if (!e.isConsumed()) {
          myPromptUpdateNeeded = true;
          myEscapePressed = false;
        }
      }
      else {
        myShellCommandHandlerHelper.processKeyPressed();
      }
    });
  }

  @NotNull
  Project getProject() {
    return myProject;
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

  private @NotNull String getLineAtCursor() {
    return processTerminalBuffer(textBuffer -> {
      TerminalLine line = textBuffer.getLine(getLineNumberAtCursor());
      return line != null ? line.getText() : "";
    });
  }

  <T> T processTerminalBuffer(@NotNull Function<TerminalTextBuffer, T> processor) {
    TerminalTextBuffer textBuffer = getTerminalPanel().getTerminalTextBuffer();
    textBuffer.lock();
    try {
      return processor.apply(textBuffer);
    }
    finally {
      textBuffer.unlock();
    }
  }

  int getLineNumberAtCursor() {
    TerminalTextBuffer textBuffer = getTerminalPanel().getTerminalTextBuffer();
    Terminal terminal = getTerminal();
    return Math.max(0, Math.min(terminal.getCursorY() - 1, textBuffer.getHeight() - 1));
  }

  public void executeCommand(@NotNull String shellCommand) throws IOException {
    String typedCommand = getTypedShellCommand();
    if (!typedCommand.isEmpty()) {
      throw new IOException("Cannot execute command when another command is typed: " + typedCommand); //NON-NLS
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
    throw new IllegalStateException("Cannot determine if there are running processes for " + connector.getClass()); //NON-NLS
  }

  @Override
  public void terminateProcess() {
    TtyConnector connector = getTtyConnector();
    if (connector instanceof ProcessTtyConnector) {
      UnixPtyProcess process = ObjectUtils.tryCast(((ProcessTtyConnector)connector).getProcess(), UnixPtyProcess.class);
      if (process != null) {
        process.hangup();
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
          if (process.isAlive()) {
            LOG.info("Terminal hasn't been terminated by SIGHUP, performing default termination");
            super.terminateProcess();
          }
        }, 1000, TimeUnit.MILLISECONDS);
        return;
      }
    }
    super.terminateProcess();
  }

  @Override
  public List<TerminalAction> getActions() {
    List<TerminalAction> baseActions = super.getActions();
    if (TerminalView.isInTerminalToolWindow(this)) {
      List<TerminalAction> actions = new ArrayList<>();
      ContainerUtil.addIfNotNull(actions, TerminalActionUtil.createTerminalAction(this, RenameTerminalSessionActionKt.ACTION_ID, true));
      return ContainerUtil.concat(baseActions, actions);
    }
    return baseActions;
  }
}
