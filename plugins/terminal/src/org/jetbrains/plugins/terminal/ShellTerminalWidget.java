// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.JBTerminalWidgetListener;
import com.intellij.terminal.actions.TerminalActionUtil;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.terminal.*;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalLineIntervalHighlighting;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.jediterm.terminal.ui.TerminalAction;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.action.RenameTerminalSessionActionKt;
import org.jetbrains.plugins.terminal.action.TerminalSplitAction;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.classic.ClassicTerminalVfsRefresher;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;
import org.jetbrains.plugins.terminal.util.TerminalUtilKt;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class ShellTerminalWidget extends JBTerminalWidget implements TerminalPanelMarker {

  private static final Logger LOG = Logger.getInstance(ShellTerminalWidget.class);

  private String myCommandHistoryFilePath;
  private List<String> myShellCommand;
  private ShellStartupOptions myStartupOptions;
  private final Prompt myPrompt = new Prompt();
  private final TerminalShellCommandHandlerHelper myShellCommandHandlerHelper;
  private final BlockingQueue<String> myCommandsToExecute = new LinkedBlockingQueue<>();

  /**
   * @deprecated use {@link #ShellTerminalWidget(Project, JBTerminalSystemSettingsProvider, Disposable)} instead
   */
  @Deprecated(forRemoval = true)
  public ShellTerminalWidget(@NotNull Project project,
                             @NotNull JBTerminalSystemSettingsProviderBase settingsProvider,
                             @NotNull Disposable parent) {
    this(project, settingsProvider instanceof JBTerminalSystemSettingsProvider p ? p : new JBTerminalSystemSettingsProvider(), parent);
  }

  public ShellTerminalWidget(@NotNull Project project,
                             @NotNull JBTerminalSystemSettingsProvider settingsProvider,
                             @NotNull Disposable parent) {
    super(project, settingsProvider, parent);
    myShellCommandHandlerHelper = new TerminalShellCommandHandlerHelper(this);

    ClassicTerminalVfsRefresher refresher = new ClassicTerminalVfsRefresher(this);
    getTerminalPanel().addPreKeyEventHandler(e -> {
      if (e.getID() != KeyEvent.KEY_PRESSED) return;
      handleAnyKeyPressed();

      if (!e.isConsumed() && e.getKeyCode() == KeyEvent.VK_ENTER) {
        String prompt = myPrompt.myPrompt;
        if (!prompt.isEmpty() && !getTypedShellCommand().isEmpty()) {
          refresher.scheduleRefreshOnCommandFinished(() -> isPromptSame(prompt));
        }
      }
      if (e.getKeyCode() == KeyEvent.VK_ENTER || TerminalShellCommandHandlerHelper.matchedExecutor(e) != null) {
        TerminalUsageTriggerCollector.triggerCommandStarted(project, getTypedShellCommand(), false);
        if (myShellCommandHandlerHelper.processEnterKeyPressed(e)) {
          e.consume();
        }
        if (!e.isConsumed()) {
          handleEnterPressed();
        }
      }
      else {
        myShellCommandHandlerHelper.processKeyPressed(e);
      }
    });
  }

  private boolean isPromptSame(@NotNull String prevPromptWhenCommandStarted) {
    return processTerminalBuffer(terminalTextBuffer -> {
      TerminalLine line = myPrompt.getLineAtCursor(terminalTextBuffer);
      String lineStr = line.getText();
      return lineStr.startsWith(prevPromptWhenCommandStarted);
    });
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

  public @Nullable String getCommandHistoryFilePath() {
    return myCommandHistoryFilePath;
  }

  @Override
  public void setShellCommand(@Nullable List<String> shellCommand) {
    myShellCommand = shellCommand != null ? List.copyOf(shellCommand) : null;
  }

  @Override
  public @Nullable List<String> getShellCommand() {
    return myShellCommand;
  }


  public void setStartupOptions(ShellStartupOptions startupOptions) {
    myStartupOptions = startupOptions;
  }

  public @Nullable ShellStartupOptions getStartupOptions() {
    return myStartupOptions;
  }

  public @NotNull String getTypedShellCommand() {
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

  @Override
  public void executeCommand(@NotNull String shellCommand) throws IOException {
    String typedCommand = getTypedShellCommand();
    if (!typedCommand.isEmpty()) {
      throw new IOException("Cannot execute command when another command is typed: " + typedCommand); //NON-NLS
    }
    myCommandsToExecute.add(shellCommand);
    doWithTerminalStarter(terminalStarter -> {
      List<String> commands = new ArrayList<>();
      myCommandsToExecute.drainTo(commands);
      for (String command : commands) {
        TerminalUtil.sendCommandToExecute(command, terminalStarter);
      }
    });
  }

  public void executeWithTtyConnector(@NotNull Consumer<TtyConnector> consumer) {
    asNewWidget().getTtyConnectorAccessor().executeWithTtyConnector(consumer);
  }

  @Override
  public boolean hasRunningCommands() throws IllegalStateException {
    TtyConnector connector = getTtyConnector();
    if (connector == null) return false;

    ProcessTtyConnector processTtyConnector = getProcessTtyConnector(connector);
    if (processTtyConnector != null) {
      return TerminalUtil.hasRunningCommands((TtyConnector)processTtyConnector);
    }
    throw new IllegalStateException("Cannot determine if there are running processes for " + connector.getClass()); //NON-NLS
  }

  @Override
  public @Nullable String getCurrentDirectory() {
    TtyConnector connector = getTtyConnector();
    if (connector == null) return null;
    return TerminalWorkingDirectoryManager.getWorkingDirectory(connector);
  }

  @Override
  public @NotNull JBTerminalSystemSettingsProvider getSettingsProvider() {
    return (JBTerminalSystemSettingsProvider)super.getSettingsProvider();
  }

  @Override
  public List<TerminalAction> getActions() {
    List<TerminalAction> actions = new ArrayList<>(super.getActions());
    if (TerminalToolWindowManager.isInTerminalToolWindow(this)) {
      ContainerUtil.addIfNotNull(actions, TerminalActionUtil.createTerminalAction(this, RenameTerminalSessionActionKt.ACTION_ID, true));
    }
    JBTerminalWidgetListener listener = getListener();
    JBTerminalSystemSettingsProvider settingsProvider = getSettingsProvider();
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getNewTabActionPresentation(), l -> {
      l.onNewSession();
      return true;
    }).withMnemonicKey(KeyEvent.VK_T));
    actions.add(TerminalActionUtil.createTerminalAction(this, settingsProvider.getCloseTabActionPresentation(), l -> {
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
    TerminalLineIntervalHighlighting highlighting = line.addCustomHighlighting(intervalStartOffset, intervalLength, style);
    getTerminalPanel().repaint();
    return highlighting;
  }

  @Override
  public void close() {
    //noinspection deprecation
    TerminalStarter starter = getTerminalStarter();
    if (starter == null) {
      super.close();
    }
    else {
      starter.close(); // close in background
      TtyConnector connector = starter.getTtyConnector();
      TerminalUtilKt.waitFor(connector, TerminalUtilKt.STOP_EMULATOR_TIMEOUT, () -> {
        if (connector.isConnected()) {
          LOG.warn("Cannot destroy " + TerminalUtilKt.getDebugName(connector));
        }
        super.close();
        return Unit.INSTANCE;
      });
    }
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

  public static @Nullable ShellTerminalWidget asShellJediTermWidget(@NotNull TerminalWidget widget) {
    return ObjectUtils.tryCast(asJediTermWidget(widget), ShellTerminalWidget.class);
  }

  public static @NotNull ShellTerminalWidget toShellJediTermWidgetOrThrow(@NotNull TerminalWidget widget) {
    return (ShellTerminalWidget)Objects.requireNonNull(asJediTermWidget(widget));
  }
}
