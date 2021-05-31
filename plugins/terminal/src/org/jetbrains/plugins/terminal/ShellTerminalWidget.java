// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.intellij.terminal.TerminalSplitAction;
import com.intellij.terminal.actions.TerminalActionUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalLineIntervalHighlighting;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalAction;
import org.jetbrains.annotations.ApiStatus;
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
import java.util.function.Function;

public class ShellTerminalWidget extends JBTerminalWidget {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);

  private final Project myProject;
  private boolean myEscapePressed = false;
  private String myCommandHistoryFilePath;
  private boolean myPromptUpdateNeeded = true;
  private String myPrompt = "";
  private final Queue<String> myPendingCommandsToExecute = new LinkedList<>();
  private final Queue<Consumer<TtyConnector>> myPendingActionsToExecute = new LinkedList<>();
  private final TerminalShellCommandHandlerHelper myShellCommandHandlerHelper;

  public ShellTerminalWidget(@NotNull Project project,
                             @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                             @NotNull Disposable parent) {
    super(project, settingsProvider, parent);
    myProject = project;
    myShellCommandHandlerHelper = new TerminalShellCommandHandlerHelper(this);

    getTerminalPanel().addPreKeyEventHandler(e -> {
      if (e.getID() != KeyEvent.KEY_PRESSED) return;
      if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        myEscapePressed = true;
      }
      handleAnyKeyPressed();

      if (e.getKeyCode() == KeyEvent.VK_ENTER || TerminalShellCommandHandlerHelper.matchedExecutor(e) != null) {
        TerminalUsageTriggerCollector.Companion.triggerCommandExecuted(myProject);
        if (myShellCommandHandlerHelper.processEnterKeyPressed(e)) {
          e.consume();
        }
        if (!e.isConsumed()) {
          handleEnterPressed();
          myEscapePressed = false;
        }
      }
      else {
        myShellCommandHandlerHelper.processKeyPressed(e);
      }
    });
  }

  @NotNull
  Project getProject() {
    return myProject;
  }

  public void handleEnterPressed() {
    myPromptUpdateNeeded = true;
  }

  public void handleAnyKeyPressed() {
    if (myPromptUpdateNeeded) {
      myPrompt = getLineAtCursor();
      if (LOG.isDebugEnabled()) {
        LOG.info("Guessed shell prompt: " + myPrompt);
      }
      myPromptUpdateNeeded = false;
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

  public void executeWithTtyConnector(@NotNull Consumer<TtyConnector> consumer) {
    TtyConnector connector = getTtyConnector();
    if (connector != null) {
      consumer.consume(connector);
    } else {
      myPendingActionsToExecute.add(consumer);
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

    Consumer<TtyConnector> consumer;
    while ((consumer = myPendingActionsToExecute.poll()) != null) {
      consumer.consume(ttyConnector);
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

    ProcessTtyConnector processTtyConnector = getProcessTtyConnector(connector);
    if (processTtyConnector != null) {
      return TerminalUtil.hasRunningCommands(processTtyConnector);
    }
    throw new IllegalStateException("Cannot determine if there are running processes for " + connector.getClass()); //NON-NLS
  }

  /**
   * @deprecated use {@link TtyConnector#close()} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2022.1")
  public void terminateProcess() {
    TtyConnector connector = getTtyConnector();
    if (connector != null) {
      connector.close();
    }
  }

  @Override
  public List<TerminalAction> getActions() {
    List<TerminalAction> actions = new ArrayList<>(super.getActions());
    if (TerminalView.isInTerminalToolWindow(this)) {
      ContainerUtil.addIfNotNull(actions, TerminalActionUtil.createTerminalAction(this, RenameTerminalSessionActionKt.ACTION_ID, true));
    }
    JBTerminalWidgetListener listener = getListener();
    JBTerminalSystemSettingsProviderBase settingsProvider = getSettingsProvider();
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getNewSessionActionPresentation(), l -> {
      l.onNewSession();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T));
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getCloseSessionActionPresentation(), l -> {
      l.onSessionClosed();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T));

    actions.add(TerminalSplitAction.create(true, getListener()).withMnemonicKey(KeyEvent.VK_V).separatorBefore(true));
    actions.add(TerminalSplitAction.create(false, getListener()).withMnemonicKey(KeyEvent.VK_H));
    if (listener != null && listener.isGotoNextSplitTerminalAvailable()) {
      actions.add(settingsProvider.getGotoNextSplitTerminalAction(listener, true));
      actions.add(settingsProvider.getGotoNextSplitTerminalAction(listener, false));
    }
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getPreviousTabActionPresentation(), l -> {
      l.onPreviousTabSelected();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T));
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getNextTabActionPresentation(), l -> {
      l.onNextTabSelected();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T));
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getMoveTabRightActionPresentation(), l -> {
      l.moveTabRight();
      return true;
    }).withMnemonicKey(KeyEvent.VK_R).withEnabledSupplier(() -> listener != null && listener.canMoveTabRight()));
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getMoveTabLeftActionPresentation(), l -> {
      l.moveTabLeft();
      return true;
    }).withMnemonicKey(KeyEvent.VK_L).withEnabledSupplier(() -> listener != null && listener.canMoveTabLeft()));
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getShowTabsActionPresentation(), l -> {
      l.showTabs();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T));
    return actions;
  }

  public @Nullable TerminalLineIntervalHighlighting highlightLineInterval(int lineNumber, int intervalStartOffset, int intervalLength,
                                                                          @NotNull TextStyle style) {
    TerminalLine line = getTerminalTextBuffer().getLine(lineNumber);
    if (line == null) {
      LOG.error("No line found");
      return null;
    }
    TerminalLineIntervalHighlighting highlighting = line.addCustomHighlighting(intervalStartOffset, intervalLength, style);
    getTerminalPanel().repaint();
    return highlighting;
  }

  public static @Nullable ProcessTtyConnector getProcessTtyConnector(@Nullable TtyConnector connector) {
    if (connector instanceof ProcessTtyConnector) {
      return (ProcessTtyConnector)connector;
    }
    if (connector instanceof ProxyTtyConnector) {
      return getProcessTtyConnector(((ProxyTtyConnector)connector).getConnector());
    }
    return null;
  }
}
