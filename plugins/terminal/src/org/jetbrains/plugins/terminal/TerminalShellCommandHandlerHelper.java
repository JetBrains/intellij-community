// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.google.common.base.Ascii;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase;
import com.intellij.terminal.TerminalShellCommandHandler;
import com.intellij.ui.BalloonImpl;
import com.intellij.ui.GotItTooltip;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.model.TerminalLineIntervalHighlighting;
import com.jediterm.terminal.model.TerminalModelListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.TerminalCommandHandlerCustomizer.Constants;
import org.jetbrains.plugins.terminal.arrangement.TerminalWorkingDirectoryManager;
import org.jetbrains.plugins.terminal.fus.TerminalUsageTriggerCollector;
import org.jetbrains.plugins.terminal.util.TerminalUtilKt;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static com.jediterm.terminal.ui.AwtTransformers.fromAwtToTerminalColor;

public final class TerminalShellCommandHandlerHelper {
  private static final Logger LOG = Logger.getInstance(TerminalShellCommandHandlerHelper.class);
  @NonNls private static final String FEATURE_ID = "terminal.shell.command.handling";
  private static final int TYPING_THRESHOLD_MS = 200;

  private static Experiments ourExperiments;
  private final ShellTerminalWidget myWidget;
  private final Alarm myAlarm;
  private volatile String myWorkingDirectory;
  private volatile Boolean myHasRunningCommands;
  private PropertiesComponent myPropertiesComponent;
  private final AtomicLong myLastKeyPressedMillis = new AtomicLong();
  private TerminalLineIntervalHighlighting myCommandHighlighting;
  private volatile SmartCommandContext myLastSmartCommandContext;
  private Disposable myNotificationDisposable;

  TerminalShellCommandHandlerHelper(@NotNull ShellTerminalWidget widget) {
    myWidget = widget;
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, widget);

    ApplicationManager.getApplication().getMessageBus().connect(myWidget).subscribe(
      Constants.getTERMINAL_COMMAND_HANDLER_TOPIC(), () -> scheduleCommandHighlighting());

    TerminalModelListener listener = () -> {
      if (System.currentTimeMillis() - myLastKeyPressedMillis.get() < TYPING_THRESHOLD_MS) {
        scheduleCommandHighlighting();
      }
    };
    TerminalUtilKt.addModelListener(widget.getTerminalTextBuffer(), myWidget, listener);
  }

  public void processKeyPressed(KeyEvent e) {
    if (isFeatureEnabled()) {
      myLastKeyPressedMillis.set(System.currentTimeMillis());
      if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiersEx() == 0 && hideNotification()) {
        e.consume();
      }
    }
  }

  private void scheduleCommandHighlighting() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(() -> {
      highlightMatchedCommand(myWidget.getProject());
    }, 0);
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
      setCommandHighlighting(null);
      return;
    }

    String command = myWidget.getTypedShellCommand().trim();
    TerminalLineIntervalHighlighting commandHighlighting = highlightCommandIfMatched(project, command);
    setCommandHighlighting(commandHighlighting);

    ApplicationManager.getApplication().invokeLater(() -> {
      showOrHideNotification(commandHighlighting);
    }, ModalityState.stateForComponent(myWidget.getTerminalPanel()));
  }

  private synchronized void setCommandHighlighting(@Nullable TerminalLineIntervalHighlighting commandHighlighting) {
    TerminalLineIntervalHighlighting oldHighlighting = myCommandHighlighting;
    if (oldHighlighting != null) {
      oldHighlighting.dispose();
      myWidget.getTerminalPanel().repaint();
    }
    myCommandHighlighting = commandHighlighting;
  }

  private boolean hideNotification() {
    boolean shown = myNotificationDisposable != null && !Disposer.isDisposed(myNotificationDisposable);
    if (shown) {
      Disposer.dispose(myNotificationDisposable);
    }
    myNotificationDisposable = null;
    return shown;
  }

  private void showOrHideNotification(@Nullable TerminalLineIntervalHighlighting commandHighlighting) {
    if (commandHighlighting == null || commandHighlighting.isDisposed()) {
      hideNotification();
      return;
    }
    if (myNotificationDisposable != null && !Disposer.isDisposed(myNotificationDisposable)) {
      return;
    }
    Disposable notificationDisposable = Disposer.newDisposable(myWidget.getTerminalPanel(), "terminal.smart_command_execution");
    String content = TerminalBundle.message("smart_command_execution.notification.text",
                                            KeymapUtil.getFirstKeyboardShortcutText(getRunAction()),
                                            KeymapUtil.getFirstKeyboardShortcutText(getDebugAction()));
    GotItTooltip tooltip = new GotItTooltip("terminal.smart_command_execution", content, notificationDisposable)
      .withHeader(TerminalBundle.message("smart_command_execution.notification.title"))
      .withLink(TerminalBundle.message("smart_command_execution.notification.configure_link.text"), () -> {
        ShowSettingsUtil.getInstance().showSettingsDialog(myWidget.getProject(), TerminalOptionsConfigurable.class);
      })
      .withPosition(Balloon.Position.below);
    if (!tooltip.canShow()) {
      Disposer.dispose(notificationDisposable);
      return;
    }
    tooltip.show(myWidget.getTerminalPanel(), (component, balloon) -> {
      Rectangle bounds = myWidget.processTerminalBuffer(buffer -> myWidget.getTerminalPanel().getBounds(commandHighlighting));
      if (bounds != null) {
        int shiftY = 0;
        if (balloon instanceof BalloonImpl &&
            BalloonImpl.getAbstractPositionFor(Balloon.Position.below) == ((BalloonImpl)balloon).getPosition()) {
          shiftY = bounds.height;
        }
        return new Point(bounds.x + bounds.width / 2, bounds.y + shiftY);
      }
      Disposer.dispose(notificationDisposable);
      return new Point(0, 0);
    });
    myNotificationDisposable = notificationDisposable;
  }

  private boolean isEnabledForProject() {
    return getPropertiesComponent().getBoolean(Constants.TERMINAL_CUSTOM_COMMAND_EXECUTION,
                                               Constants.TERMINAL_CUSTOM_COMMAND_EXECUTION_DEFAULT);
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
      workingDirectory = StringUtil.notNullize(TerminalWorkingDirectoryManager.getWorkingDirectory(myWidget.asNewWidget()));
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

  private @Nullable TerminalLineIntervalHighlighting highlightCommandIfMatched(@NotNull Project project, @NotNull String command) {
    if (command.isEmpty()) {
      myLastSmartCommandContext = null;
      return null;
    }
    String workingDirectory = getWorkingDirectory();
    boolean localSession = !hasRunningCommands();
    TerminalShellCommandHandler handler = ContainerUtil.find(TerminalShellCommandHandler.Companion.getEP().getExtensionList(),
                                                             it -> it.matches(project, workingDirectory, localSession, command));
    if (handler == null) {
      myLastSmartCommandContext = null;
      return null;
    }
    TerminalLineIntervalHighlighting highlighting = myWidget.processTerminalBuffer(textBuffer -> {
      int cursorLine = myWidget.getLineNumberAtCursor();
      if (cursorLine < 0 || cursorLine >= textBuffer.getHeight()) {
        return null;
      }
      String lineText = textBuffer.getLine(cursorLine).getText();
      int commandStartInd = lineText.lastIndexOf(command);
      if (commandStartInd < 0) {
        return null;
      }
      TextStyle textStyle = getSmartCommandExecutionStyle();
      if (textStyle == null) {
        return null;
      }
      return myWidget.highlightLineInterval(cursorLine, commandStartInd, command.length(), textStyle);
    });
    if (highlighting != null) {
      myLastSmartCommandContext = new SmartCommandContext(handler, myWidget.getTtyConnector(),
                                                          project, workingDirectory, localSession, command);
    }
    else {
      myLastSmartCommandContext = null;
    }
    return highlighting;
  }

  private static @Nullable TextStyle getSmartCommandExecutionStyle() {
    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(JBTerminalSystemSettingsProviderBase.COMMAND_TO_RUN_USING_IDE_KEY);
    if (attributes == null) {
      return null;
    }
    return new TextStyle(fromAwtToTerminalColor(attributes.getForegroundColor()), fromAwtToTerminalColor(attributes.getBackgroundColor()));
  }

  public boolean processEnterKeyPressed(@NotNull KeyEvent keyPressed) {
    if (!isFeatureEnabled() || !isEnabledForProject()) {
      onShellCommandExecuted();
      return false;
    }
    myLastKeyPressedMillis.set(System.currentTimeMillis());
    String command = myWidget.getTypedShellCommand().trim();
    if (LOG.isDebugEnabled()) {
      LOG.debug("typed shell command to execute: " + command);
    }
    myAlarm.cancelAllRequests();

    Executor executor = matchedExecutor(keyPressed);
    SmartCommandContext smartCommandContext = getLastSmartCommandContextIfMatched(command);
    if (smartCommandContext != null && executor != null) {
      smartCommandContext.execute(executor);
      return true;
    }
    if (smartCommandContext != null) {
      smartCommandContext.triggerCommandExecuted(false);
    }
    onShellCommandExecuted();
    return false;
  }

  private @Nullable SmartCommandContext getLastSmartCommandContextIfMatched(@NotNull String command) {
    SmartCommandContext smartCommandContext = myLastSmartCommandContext;
    return smartCommandContext != null && smartCommandContext.myCommand.equals(command) ? smartCommandContext : null;
  }

  private void onShellCommandExecuted() {
    myWorkingDirectory = null;
    myHasRunningCommands = null;
  }

  static @Nullable Executor matchedExecutor(@NotNull KeyEvent e) {
    if (matchAction(e, getRunAction())) {
      return DefaultRunExecutor.getRunExecutorInstance();
    }
    if (matchAction(e, getDebugAction())) {
      return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
    }
    return null;
  }

  private static boolean matchAction(@NotNull KeyEvent e, @NotNull AnAction action) {
    KeyboardShortcut eventShortcut = new KeyboardShortcut(KeyStroke.getKeyStrokeForEvent(e), null);
    return ContainerUtil.exists(action.getShortcutSet().getShortcuts(), sc -> sc.isKeyboard() && sc.startsWith(eventShortcut));
  }

  private static @NotNull AnAction getRunAction() {
    return Objects.requireNonNull(ActionManager.getInstance().getAction("Terminal.SmartCommandExecution.Run"));
  }

  private static @NotNull AnAction getDebugAction() {
    return Objects.requireNonNull(ActionManager.getInstance().getAction("Terminal.SmartCommandExecution.Debug"));
  }

  private static class SmartCommandContext {

    private final TerminalShellCommandHandler myHandler;
    private final TtyConnector myTtyConnector;
    private final Project myProject;
    private final String myWorkingDirectory;
    private final boolean myLocalSession;
    private final String myCommand;

    private SmartCommandContext(@NotNull TerminalShellCommandHandler handler,
                                @NotNull TtyConnector ttyConnector,
                                @NotNull Project project,
                                @Nullable String workingDirectory,
                                boolean localSession,
                                @NotNull String command) {
      myHandler = handler;
      myTtyConnector = ttyConnector;
      myProject = project;
      myWorkingDirectory = workingDirectory;
      myLocalSession = localSession;
      myCommand = command;
    }

    private void execute(@NotNull Executor executor) {
      triggerCommandExecuted(true);
      TerminalShellCommandHandler.Companion.executeShellCommandHandler(myProject, myWorkingDirectory, myLocalSession, myCommand, executor);
      clearTypedCommand(myCommand);
    }

    private void clearTypedCommand(@NotNull String command) {
      byte[] array = new byte[command.length()];
      Arrays.fill(array, Ascii.BS);
      try {
        myTtyConnector.write(array);
      }
      catch (IOException e) {
        LOG.info("Cannot clear shell command " + command, e);
      }
    }

    private void triggerCommandExecuted(boolean inSmartWay) {
      TerminalUsageTriggerCollector.triggerSmartCommand(myProject, myWorkingDirectory, myLocalSession, myCommand, myHandler, inSmartWay);
    }
  }
}
