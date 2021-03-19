// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.base.Ascii;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.terminal.TerminalShellCommandHandler;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLineIntervalHighlighting;
import com.jediterm.terminal.model.TerminalModelListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.shellCommandRunner.TerminalDebugSmartCommandAction;
import org.jetbrains.plugins.terminal.shellCommandRunner.TerminalExecutorAction;
import org.jetbrains.plugins.terminal.shellCommandRunner.TerminalRunSmartCommandAction;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TerminalShellCommandHandlerHelper {
  private static final Logger LOG = Logger.getInstance(TerminalShellCommandHandler.class);
  @NonNls private static final String TERMINAL_CUSTOM_COMMANDS_GOT_IT = "TERMINAL_CUSTOM_COMMANDS_GOT_IT";
  @NonNls private static final String GOT_IT = "got_it";
  @NonNls private static final String FEATURE_ID = "terminal.shell.command.handling";

  private static Experiments ourExperiments;
  private static final NotificationGroup ourToolWindowGroup =
    NotificationGroup.toolWindowGroup("Terminal", TerminalToolWindowFactory.TOOL_WINDOW_ID);
  private final ShellTerminalWidget myWidget;
  private final Alarm myAlarm;
  private volatile String myWorkingDirectory;
  private volatile Boolean myHasRunningCommands;
  private PropertiesComponent myPropertiesComponent;
  private final SingletonNotificationManager mySingletonNotificationManager =
    new SingletonNotificationManager(ourToolWindowGroup, NotificationType.INFORMATION, null);
  private final AtomicBoolean myKeyPressed = new AtomicBoolean(false);
  private TerminalLineIntervalHighlighting myCurrentHighlighting;

  TerminalShellCommandHandlerHelper(@NotNull ShellTerminalWidget widget) {
    myWidget = widget;
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, widget);

    ApplicationManager.getApplication().getMessageBus().connect(myWidget).subscribe(
      TerminalCommandHandlerCustomizer.Companion.getTERMINAL_COMMAND_HANDLER_TOPIC(), () -> scheduleCommandHighlighting());

    TerminalModelListener listener = () -> {
      if (myKeyPressed.compareAndSet(true, false)) {
        scheduleCommandHighlighting();
      }
    };
    widget.getTerminalTextBuffer().addModelListener(listener);
    Disposer.register(myWidget, () -> widget.getTerminalTextBuffer().removeModelListener(listener));
  }

  public void processKeyPressed() {
    if (isFeatureEnabled()) {
      myKeyPressed.set(true);
      scheduleCommandHighlighting();
    }
  }

  private void scheduleCommandHighlighting() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> { highlightMatchedCommand(myWidget.getProject()); }, 0);
  }

  public static boolean isFeatureEnabled() {
    Experiments experiments = ourExperiments;
    if (experiments == null) {
      experiments = ReadAction.compute(() -> {
        return ApplicationManager.getApplication().isDisposed() ? null : Experiments.getInstance();
      });
      ourExperiments = experiments;
    }
    return experiments != null && experiments.isFeatureEnabled(FEATURE_ID);
  }

  private void highlightMatchedCommand(@NotNull Project project) {
    if (!isEnabledForProject()) {
      setHighlighting(null);
      return;
    }

    String command = myWidget.getTypedShellCommand().trim();
    TerminalLineIntervalHighlighting commandHighlighting = null;
    if (TerminalShellCommandHandler.Companion.matches(project, getWorkingDirectory(), !hasRunningCommands(), command)) {
      commandHighlighting = doHighlight(command);
    }
    setHighlighting(commandHighlighting);

    //show notification
    if (getPropertiesComponent().getBoolean(TERMINAL_CUSTOM_COMMANDS_GOT_IT, false)) {
      return;
    }

    if (commandHighlighting != null) {
      String title = TerminalBundle.message("smart_command_execution.notification.title");
      String content = TerminalBundle.message("smart_command_execution.notification.text",
                                              KeymapUtil.getFirstKeyboardShortcutText(getRunAction()),
                                              KeymapUtil.getFirstKeyboardShortcutText(getDebugAction()),
                                              ShowSettingsUtil.getSettingsMenuName(),
                                              GOT_IT);
      NotificationListener.Adapter listener = new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent e) {
          if (GOT_IT.equals(e.getDescription())) {
            getPropertiesComponent().setValue(TERMINAL_CUSTOM_COMMANDS_GOT_IT, true, false);
          }
        }
      };
      mySingletonNotificationManager.notify(title, content, project, listener);
    }
  }

  private synchronized void setHighlighting(@Nullable TerminalLineIntervalHighlighting highlighting) {
    TerminalLineIntervalHighlighting oldHighlighting = myCurrentHighlighting;
    if (oldHighlighting != null) {
      oldHighlighting.dispose();
      myWidget.getTerminalPanel().repaint();
    }
    myCurrentHighlighting = highlighting;
  }

  private boolean isEnabledForProject() {
    return getPropertiesComponent().getBoolean(TerminalCommandHandlerCustomizer.TERMINAL_CUSTOM_COMMAND_EXECUTION, true);
  }

  @NotNull
  private PropertiesComponent getPropertiesComponent() {
    PropertiesComponent propertiesComponent = myPropertiesComponent;
    if (propertiesComponent == null) {
      propertiesComponent = ReadAction.compute(() -> PropertiesComponent.getInstance());
      myPropertiesComponent = propertiesComponent;
    }
    return propertiesComponent;
  }

  @Nullable
  private String getWorkingDirectory() {
    String workingDirectory = myWorkingDirectory;
    if (workingDirectory == null) {
      workingDirectory = StringUtil.notNullize(TerminalWorkingDirectoryManager.getWorkingDirectory(myWidget, null));
      myWorkingDirectory = workingDirectory;
    }
    return StringUtil.nullize(workingDirectory);
  }

  private boolean hasRunningCommands() {
    Boolean hasRunningCommands = myHasRunningCommands;
    if (hasRunningCommands == null) {
      hasRunningCommands = myWidget.hasRunningCommands();
      myHasRunningCommands = hasRunningCommands;
    }
    return hasRunningCommands;
  }

  private @Nullable TerminalLineIntervalHighlighting doHighlight(@NotNull String command) {
    if (command.length() == 0) {
      return null;
    }
    return myWidget.processTerminalBuffer(textBuffer -> {
      int cursorLine = myWidget.getLineNumberAtCursor();
      if (cursorLine < 0 || cursorLine >= textBuffer.getHeight()) {
        return null;
      }
      String lineText = textBuffer.getLine(cursorLine).getText();
      int commandStartInd = lineText.lastIndexOf(command);
      if (commandStartInd < 0) {
        return null;
      }
      TextStyle style = new TextStyle(null, getSmartCommandExecutionHighlightingColor());
      return myWidget.highlightLineInterval(cursorLine, commandStartInd, command.length(), style);
    });
  }

  private static @NotNull TerminalColor getSmartCommandExecutionHighlightingColor() {
    return new TerminalColor(() -> new JBColor(0xCFEFC6, 0x40503C));
  }

  public boolean processEnterKeyPressed(@NotNull KeyEvent keyPressed) {
    if (!isFeatureEnabled() || !isEnabledForProject()) {
      onShellCommandExecuted();
      return false;
    }
    String command = myWidget.getTypedShellCommand().trim();
    if (LOG.isDebugEnabled()) {
      LOG.debug("typed shell command to execute: " + command);
    }
    myAlarm.cancelAllRequests();

    Project project = myWidget.getProject();
    String workingDirectory = getWorkingDirectory();
    boolean localSession = !hasRunningCommands();
    if (!TerminalShellCommandHandler.Companion.matches(project, workingDirectory, localSession, command)) {
      onShellCommandExecuted();
      return false;
    }

    TerminalShellCommandHandler handler = TerminalShellCommandHandler.Companion.getEP().getExtensionList().stream()
      .filter(it -> it.matches(project, workingDirectory, localSession, command))
      .findFirst()
      .orElseThrow(() -> new RuntimeException("Cannot find matching command handler."));

    Executor executor = matchedExecutor(keyPressed);
    if (executor == null) {
      onShellCommandExecuted();
      TerminalUsageTriggerCollector.Companion.triggerSmartCommand(project, workingDirectory, localSession, command, handler, false);
      return false;
    }

    TerminalUsageTriggerCollector.Companion.triggerSmartCommand(project, workingDirectory, localSession, command, handler, true);
    TerminalShellCommandHandler.Companion.executeShellCommandHandler(myWidget.getProject(), getWorkingDirectory(),
                                                                     !hasRunningCommands(), command, executor);
    clearTypedCommand(command);
    return true;
  }

  private void onShellCommandExecuted() {
    myWorkingDirectory = null;
    myHasRunningCommands = null;
  }

  private void clearTypedCommand(@NotNull String command) {
    TtyConnector connector = myWidget.getTtyConnector();
    byte[] array = new byte[command.length()];
    Arrays.fill(array, Ascii.BS);
    try {
      connector.write(array);
    }
    catch (IOException e) {
      LOG.info("Cannot clear shell command " + command, e);
    }
  }

  @Nullable
  static Executor matchedExecutor(@NotNull KeyEvent e) {
    if (matchedRunAction(e) != null) {
      return DefaultRunExecutor.getRunExecutorInstance();
    } else if (matchedDebugAction(e) != null) {
      return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
    } else {
      return null;
    }
  }

  private static TerminalExecutorAction matchedRunAction(@NotNull KeyEvent e) {
    final KeyboardShortcut eventShortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(e), null);
    AnAction action = getRunAction();
    return action instanceof TerminalRunSmartCommandAction
           && Arrays.stream(action.getShortcutSet().getShortcuts()).anyMatch(sc -> sc.isKeyboard() && sc.startsWith(eventShortcut))
           ? ((TerminalRunSmartCommandAction)action)
           : null;
  }

  private static TerminalExecutorAction matchedDebugAction(@NotNull KeyEvent e) {
    final KeyboardShortcut eventShortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(e), null);
    AnAction action = getDebugAction();
    return action instanceof TerminalDebugSmartCommandAction
           && Arrays.stream(action.getShortcutSet().getShortcuts()).anyMatch(sc -> sc.isKeyboard() && sc.startsWith(eventShortcut))
           ? ((TerminalDebugSmartCommandAction)action)
           : null;
  }

  @NotNull
  private static AnAction getRunAction() {
    return Objects.requireNonNull(ActionManager.getInstance().getAction("Terminal.SmartCommandExecution.Run"));
  }

  @NotNull
  private static AnAction getDebugAction() {
    return Objects.requireNonNull(ActionManager.getInstance().getAction("Terminal.SmartCommandExecution.Debug"));
  }
}
