// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.intellij.terminal.actions.TerminalActionUtil;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.pty.PtyProcessTtyConnector;
import com.jediterm.terminal.ProcessTtyConnector;
import com.jediterm.terminal.Terminal;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalLineIntervalHighlighting;
import com.jediterm.terminal.model.TerminalModelListener;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalAction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.RenameTerminalSessionActionKt;
import org.jetbrains.plugins.terminal.action.TerminalSplitAction;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ShellTerminalWidget extends JBTerminalWidget {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);
  private static final long VFS_REFRESH_DELAY_MS = 500;

  private boolean myEscapePressed = false;
  private String myCommandHistoryFilePath;
  private final Prompt myPrompt = new Prompt();
  private final Queue<String> myPendingCommandsToExecute = new LinkedList<>();
  private final Queue<Consumer<TtyConnector>> myPendingActionsToExecute = new LinkedList<>();
  private final TerminalShellCommandHandlerHelper myShellCommandHandlerHelper;

  private final Alarm myVfsRefreshAlarm;
  private final TerminalModelListener myVfsRefreshModelListener;
  private volatile String myPrevPromptWhenCommandStarted;

  public ShellTerminalWidget(@NotNull Project project,
                             @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                             @NotNull Disposable parent) {
    super(project, settingsProvider, parent);
    myShellCommandHandlerHelper = new TerminalShellCommandHandlerHelper(this);

    myVfsRefreshAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    myVfsRefreshModelListener = () -> {
      myVfsRefreshAlarm.cancelAllRequests();
      myVfsRefreshAlarm.addRequest(this::refreshVfsIfPromptIsShown, VFS_REFRESH_DELAY_MS);
    };

    getTerminalPanel().addPreKeyEventHandler(e -> {
      if (e.getID() != KeyEvent.KEY_PRESSED) return;
      if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
        myEscapePressed = true;
      }
      handleAnyKeyPressed();

      if (!e.isConsumed() && e.getKeyCode() == KeyEvent.VK_ENTER) {
        String prompt = myPrompt.myPrompt;
        if (!prompt.isEmpty() && !getTypedShellCommand().isEmpty()) {
          myVfsRefreshAlarm.cancelAllRequests();
          getTerminalTextBuffer().removeModelListener(myVfsRefreshModelListener);
          myPrevPromptWhenCommandStarted = prompt;
          if (!getTerminalTextBuffer().isUsingAlternateBuffer()) {
            getTerminalTextBuffer().addModelListener(myVfsRefreshModelListener);
          }
        }
      }
      if (e.getKeyCode() == KeyEvent.VK_ENTER || TerminalShellCommandHandlerHelper.matchedExecutor(e) != null) {
        TerminalUsageTriggerCollector.Companion.triggerCommandExecuted(project);
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
    Disposer.register(this, () -> getTerminalTextBuffer().removeModelListener(myVfsRefreshModelListener));
  }

  private void refreshVfsIfPromptIsShown() {
    String promptWhenCommandStarted = myPrevPromptWhenCommandStarted;
    if (promptWhenCommandStarted != null) {
      processTerminalBuffer(terminalTextBuffer -> {
        TerminalLine line = myPrompt.getLineAtCursor(terminalTextBuffer);
        String lineStr = line.getText();
        if (lineStr.startsWith(promptWhenCommandStarted)) {
          // A shown prompt probably suggests that last command has been terminated
          SaveAndSyncHandler.getInstance().scheduleRefresh();
          myPrevPromptWhenCommandStarted = null;
          myVfsRefreshAlarm.cancelAllRequests();
          getTerminalTextBuffer().removeModelListener(myVfsRefreshModelListener);
        }
        return null;
      });
    }
  }

  public void handleEnterPressed() {
    myPrompt.reset();
  }

  public void handleAnyKeyPressed() {
    myPrompt.onKeyPressed();
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
    return myPrompt.getTypedShellCommand();
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
  public @Nls @Nullable String getDefaultSessionName() {
    ProcessTtyConnector connector = getProcessTtyConnector();
    if (connector instanceof PtyProcessTtyConnector) {
      // use name from settings for local terminal
      return TerminalOptionsProvider.getInstance().getTabName();
    }
    return super.getDefaultSessionName();
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

    actions.add(TerminalSplitAction.create(true, getListener()).separatorBefore(true));
    actions.add(TerminalSplitAction.create(false, getListener()));
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

  @Override
  public @Nullable ProcessTtyConnector getProcessTtyConnector() {
    return getProcessTtyConnector(getTtyConnector());
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

  private final class Prompt {
    private volatile @NotNull String myPrompt = "";
    private final AtomicInteger myTypings = new AtomicInteger(0);
    private TerminalLine myTerminalLine;
    private int myMaxCursorX = -1;

    private void reset() {
      myTypings.set(0);
      myTerminalLine = null;
      myMaxCursorX = -1;
    }

    private void onKeyPressed() {
      TerminalLine terminalLine = processTerminalBuffer(this::getLineAtCursor);
      if (terminalLine != myTerminalLine) {
        myTypings.set(0);
        myTerminalLine = terminalLine;
        myMaxCursorX = -1;
      }
      String prompt = getLineTextUpToCursor(terminalLine);
      if (myTypings.get() == 0) {
        myPrompt = prompt;
        myTerminalLine = terminalLine;
        if (LOG.isDebugEnabled()) {
          LOG.debug("Guessed shell prompt: " + myPrompt);
        }
      }
      else {
        if (prompt.startsWith(myPrompt)) {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Guessed prompt confirmed by typing# " + (myTypings.get() + 1));
          }
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Prompt rejected by typing#" + (myTypings.get() + 1) + ", new prompt: " + prompt);
          }
          myPrompt = prompt;
          myTypings.set(1);
        }
      }
      myTypings.incrementAndGet();
    }

    private @NotNull String getTypedShellCommand() {
      if (myTypings.get() == 0) {
        return "";
      }
      TerminalLine terminalLine = processTerminalBuffer(this::getLineAtCursor);
      if (terminalLine != myTerminalLine) {
        return "";
      }
      String lineTextUpToCursor = getLineTextUpToCursor(terminalLine);
      if (lineTextUpToCursor.startsWith(myPrompt)) {
        return lineTextUpToCursor.substring(myPrompt.length());
      }
      return "";
    }

    private @NotNull TerminalLine getLineAtCursor(@NotNull TerminalTextBuffer textBuffer) {
      return textBuffer.getLine(getLineNumberAtCursor());
    }

    private @NotNull String getLineTextUpToCursor(@Nullable TerminalLine line) {
      if (line == null) return "";
      return processTerminalBuffer(textBuffer -> {
        int cursorX = getTerminal().getCursorX() - 1;
        String lineStr = line.getText();
        int maxCursorX = Math.max(myMaxCursorX, cursorX);
        while (maxCursorX < lineStr.length() && !Character.isWhitespace(lineStr.charAt(maxCursorX))) {
          maxCursorX++;
        }
        myMaxCursorX = maxCursorX;
        return lineStr.substring(0, Math.min(maxCursorX, lineStr.length()));
      });
    }
  }
}
