// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.terminal;

import com.google.common.base.Ascii;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.terminal.TerminalDebugSmartCommandAction;
import com.intellij.terminal.TerminalExecutorAction;
import com.intellij.terminal.TerminalRunSmartCommandAction;
import com.intellij.terminal.TerminalShellCommandHandler;
import com.intellij.util.Alarm;
import com.jediterm.terminal.StyledTextConsumerAdapter;
import com.jediterm.terminal.SubstringFinder;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.CharBuffer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

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

  TerminalShellCommandHandlerHelper(@NotNull ShellTerminalWidget widget) {
    myWidget = widget;
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, widget);
  }

  public void processKeyPressed() {
    if (isFeatureEnabled()) {
      myAlarm.cancelAllRequests();
      scheduleCommandHighlighting();

      ApplicationManager.getApplication().getMessageBus().connect().subscribe(
        TerminalCommandHandlerCustomizer.Companion.getTERMINAL_COMMAND_HANDLER_TOPIC(),
        () -> myAlarm.addRequest(() -> { scheduleCommandHighlighting(); }, 50));
    }
  }

  private void scheduleCommandHighlighting() {
    myAlarm.addRequest(() -> { highlightMatchedCommand(myWidget.getProject()); }, 50);
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
      myWidget.getTerminalPanel().setFindResult(null);
      return;
    }

    //highlight matched command
    String command = myWidget.getTypedShellCommand().trim();
    SubstringFinder.FindResult result =
      TerminalShellCommandHandler.Companion.matches(project, getWorkingDirectory(), !hasRunningCommands(), command)
      ? searchMatchedCommand(command) : null;
    myWidget.getTerminalPanel().setFindResult(result);

    //show notification
    if (getPropertiesComponent().getBoolean(TERMINAL_CUSTOM_COMMANDS_GOT_IT, false)) {
      return;
    }

    if (result != null) {
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

  private @Nullable SubstringFinder.FindResult searchMatchedCommand(@NotNull String pattern) {
    if (pattern.length() == 0) {
      return null;
    }

    return myWidget.processTerminalBuffer(textBuffer -> {
      int cursorLine = myWidget.getLineNumberAtCursor();
      if (cursorLine < 0 || cursorLine >= textBuffer.getHeight()) {
        return null;
      }
      String lineText = textBuffer.getLine(cursorLine).getText();
      int patternStartInd = lineText.lastIndexOf(pattern);
      if (patternStartInd < 0) {
        return null;
      }
      SubstringFinder finder = new SubstringFinder(pattern, true) {
        @Override
        public boolean accept(@NotNull FindResult.FindItem item) {
          return item.getStart().x >= patternStartInd;
        }
      };
      textBuffer.processScreenLines(cursorLine, 1, new StyledTextConsumerAdapter() {
        @Override
        public void consume(int x, int y, @NotNull TextStyle style, @NotNull CharBuffer characters, int startRow) {
          for (int i = 0; i < characters.length(); i++) {
            finder.nextChar(x, y - startRow, characters, i);
          }
        }
      });
      return finder.getResult();
    });
  }

  public boolean processEnterKeyPressed(@NotNull String command, @NotNull KeyEvent keyPressed) {
    if (!isFeatureEnabled() || !isEnabledForProject()) {
      onShellCommandExecuted();
      return false;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("typed shell command to execute: " + command);
    }
    myAlarm.cancelAllRequests();

    TerminalExecutorAction matchedExecutorAction = matchedExecutorAction(keyPressed);
    if (matchedExecutorAction == null) {
      onShellCommandExecuted();
      return false;
    }

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
    TerminalUsageTriggerCollector.Companion.triggerSmartCommandExecuted(project, workingDirectory, localSession, command, handler);
    TerminalShellCommandHandler.Companion.executeShellCommandHandler(myWidget.getProject(), getWorkingDirectory(),
                                                                     !hasRunningCommands(), command, matchedExecutorAction);
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

  static TerminalExecutorAction matchedExecutorAction(@NotNull KeyEvent e) {
    TerminalExecutorAction action = matchedRunAction(e);
    return action != null ? action : matchedDebugAction(e);
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
