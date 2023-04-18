// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.actions.ShowLogAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.Alarm;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.TerminalSession;
import com.pty4j.windows.WinPtyException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.exp.TerminalWidgetImpl;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public abstract class AbstractTerminalRunner<T extends Process> {
  private static final Logger LOG = Logger.getInstance(AbstractTerminalRunner.class);
  @NotNull
  protected final Project myProject;
  private final JBTerminalSystemSettingsProvider mySettingsProvider;
  private final ThreadLocal<ShellStartupOptions> myStartupOptionsThreadLocal = new ThreadLocal<>();

  public JBTerminalSystemSettingsProvider getSettingsProvider() {
    return mySettingsProvider;
  }

  public AbstractTerminalRunner(@NotNull Project project) {
    myProject = project;
    mySettingsProvider = new JBTerminalSystemSettingsProvider();
  }

  /**
   * @deprecated {@link TerminalToolWindowManager} instead
   */
  @Deprecated
  public void run() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, TerminalBundle.message("progress.title.running.terminal"), false) {
      @SuppressWarnings("DialogTitleCapitalization")
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText(TerminalBundle.message("progress.text.running.terminal"));
        try {
          doRun();
        }
        catch (final Exception e) {
          LOG.warn("Error running terminal", e);

          UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(AbstractTerminalRunner.this.getProject(), e.getMessage(), getTitle()));
        }
      }
    });
  }

  private void doRun() {
    // Create Server process
    try {
      final T process = createProcess(new ShellStartupOptions.Builder().build());

      UIUtil.invokeLaterIfNeeded(() -> initConsoleUI(process));
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  /**
   * Used to calculate or adjust the options (like startup command, env variables and so on)
   * that will be used to configure the process and display the terminal.
   *
   * @return options that will be supplied to {@link #createProcess(ShellStartupOptions)}
   */
  public @NotNull ShellStartupOptions configureStartupOptions(@NotNull ShellStartupOptions baseOptions) {
    return baseOptions;
  }

  public @NotNull T createProcess(@NotNull ShellStartupOptions startupOptions) throws ExecutionException {
    //noinspection removal
    return createProcess(new TerminalProcessOptions(startupOptions.getWorkingDirectory(), startupOptions.getInitialTermSize()), null);
  }

  protected abstract ProcessHandler createProcessHandler(T process);

  /**
   * @deprecated use {@link #createTerminalWidget(Disposable, VirtualFile, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  public JBTerminalWidget createTerminalWidget(@NotNull Disposable parent, @Nullable VirtualFile currentWorkingDirectory) {
    return createTerminalWidget(parent, getParentDirectoryPath(currentWorkingDirectory), true);
  }

  /**
   * @deprecated use {@link AbstractTerminalRunner#createTerminalWidget(Disposable, String, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  @NotNull
  protected JBTerminalWidget createTerminalWidget(@NotNull Disposable parent,
                                                  @Nullable VirtualFile currentWorkingDirectory,
                                                  boolean deferSessionStartUntilUiShown) {
    return createTerminalWidget(parent, getParentDirectoryPath(currentWorkingDirectory), deferSessionStartUntilUiShown);
  }

  /**
   * @deprecated use {@link #startShellTerminalWidget(Disposable, ShellStartupOptions, boolean)}
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated(forRemoval = true)
  public @NotNull JBTerminalWidget createTerminalWidget(@NotNull Disposable parent,
                                                        @Nullable String currentWorkingDirectory,
                                                        boolean deferSessionStartUntilUiShown) {
    ShellStartupOptions startupOptions = getStartupOptions(currentWorkingDirectory);
    TerminalWidget terminalWidget = createShellTerminalWidget(parent, startupOptions);
    JBTerminalWidget jediTermWidget = JBTerminalWidget.asJediTermWidget(terminalWidget);
    if (jediTermWidget == null) {
      Disposer.dispose(terminalWidget);
      throw new IncompatibleWidgetException();
    }
    scheduleOpenSessionInDirectory(terminalWidget, startupOptions, deferSessionStartUntilUiShown);
    return jediTermWidget;
  }

  private @NotNull ShellStartupOptions getStartupOptions(@Nullable String workingDirectory) {
    ShellStartupOptions startupOptions = myStartupOptionsThreadLocal.get();
    if (startupOptions != null && Objects.equals(startupOptions.getWorkingDirectory(), workingDirectory)) {
      return startupOptions;
    }
    return ShellStartupOptionsKt.shellStartupOptions(workingDirectory);
  }

  public @NotNull TerminalWidget startShellTerminalWidget(@NotNull Disposable parent,
                                                          @NotNull ShellStartupOptions startupOptions,
                                                          boolean deferSessionStartUntilUiShown) {
    try {
      myStartupOptionsThreadLocal.set(startupOptions);
      return createTerminalWidget(parent, startupOptions.getWorkingDirectory(), deferSessionStartUntilUiShown).asNewWidget();
    }
    catch (IncompatibleWidgetException e) {
      TerminalWidget widget = createShellTerminalWidget(parent, startupOptions);
      scheduleOpenSessionInDirectory(widget, startupOptions, deferSessionStartUntilUiShown);
      return widget;
    }
    finally {
      myStartupOptionsThreadLocal.remove();
    }
  }

  protected @NotNull TerminalWidget createShellTerminalWidget(@NotNull Disposable parent, @NotNull ShellStartupOptions startupOptions) {
    return new ShellTerminalWidget(myProject, mySettingsProvider, parent).asNewWidget();
  }

  private void scheduleOpenSessionInDirectory(@NotNull TerminalWidget terminalWidget,
                                              @NotNull ShellStartupOptions startupOptions,
                                              boolean deferSessionStartUntilUiShown) {
    if (deferSessionStartUntilUiShown) {
      doWhenFirstShownAndLaidOut(terminalWidget, () -> openSessionInDirectory(terminalWidget, startupOptions));
    }
    else {
      openSessionInDirectory(terminalWidget, startupOptions);
    }
  }

  private static void doWhenFirstShownAndLaidOut(@NotNull TerminalWidget terminalWidget, @NotNull Runnable action) {
    JComponent component = terminalWidget.getComponent();
    UiNotifyConnector.doWhenFirstShown(component, () -> {
      Dimension size = component.getSize();
      if (size.width != 0 || size.height != 0) {
        LOG.debug("Terminal component layout is already done");
        action.run();
        return;
      }
      long startNano = System.nanoTime();
      CompletableFuture<Void> future = new CompletableFuture<>();
      ComponentAdapter resizeListener = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (LOG.isDebugEnabled()) {
            LOG.info("Terminal component layout took " + TimeoutUtil.getDurationMillis(startNano) + "ms");
          }
          future.complete(null);
        }
      };
      component.addComponentListener(resizeListener);

      Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, terminalWidget);
      alarm.addRequest(() -> {
        LOG.debug("Terminal component layout is timed out (>1000ms), starting terminal with default size");
        future.complete(null);
      }, 1000, ModalityState.stateForComponent(component));

      future.thenAccept(result -> {
        Disposer.dispose(alarm);
        component.removeComponentListener(resizeListener);
        action.run();
      });
    });
  }

  private void initConsoleUI(final T process) {
    final Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar("TerminalRunner", toolbarActions, false);


    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);


    actionToolbar.setTargetComponent(panel);

    ProcessHandler processHandler = createProcessHandler(process);

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(null, processHandler, panel, getDefaultTabTitle());

    contentDescriptor.setAutoFocusContent(true);

    toolbarActions.add(createCloseAction(defaultExecutor, contentDescriptor));

    final JBTerminalSystemSettingsProvider provider = new JBTerminalSystemSettingsProvider();
    JBTerminalWidget widget = new JBTerminalWidget(myProject, provider, contentDescriptor);

    createAndStartSession(widget, createTtyConnector(process));

    panel.add(widget.getComponent(), BorderLayout.CENTER);

    showConsole(defaultExecutor, contentDescriptor, widget.getComponent());

    processHandler.startNotify();
  }

  public @Nullable String getCurrentWorkingDir(@Nullable TerminalTabState state) {
    return state != null ? state.myWorkingDirectory : null;
  }

  private static void createAndStartSession(@NotNull JBTerminalWidget terminal, @NotNull TtyConnector ttyConnector) {
    TerminalSession session = terminal.createTerminalSession(ttyConnector);
    session.start();
  }

  public @Nullable @NlsContexts.TabTitle String getDefaultTabTitle() {
    return null;
  }

  /**
   * @deprecated use {@link #getDefaultTabTitle()} instead
   */
  @SuppressWarnings("unused")
  @Deprecated
  protected String getTerminalConnectionName(T process) {
    return getDefaultTabTitle();
  }

  public abstract @NotNull TtyConnector createTtyConnector(@NotNull T process);

  protected AnAction createCloseAction(final Executor defaultExecutor, final RunContentDescriptor myDescriptor) {
    return new CloseAction(defaultExecutor, myDescriptor, myProject);
  }

  protected void showConsole(Executor defaultExecutor, @NotNull RunContentDescriptor myDescriptor, final Component toFocus) {
    // Show in run toolwindow
    RunContentManager.getInstance(myProject).showRunContent(defaultExecutor, myDescriptor);

    // Request focus
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(defaultExecutor.getId());
    if (toolWindow != null) {
      toolWindow.activate(() -> IdeFocusManager.getInstance(myProject).requestFocus(toFocus, true));
    }
  }

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  /**
   * @deprecated use {@link #getDefaultTabTitle()} instead
   */
  @Deprecated
  public String runningTargetName() {
    return getDefaultTabTitle();
  }

  /**
   * @return true if all live terminal sessions created with this runner
   *              should be recreated when the project is opened for the next time
   */
  public boolean isTerminalSessionPersistent() {
    return true;
  }

  @Nullable
  private static String getParentDirectoryPath(@Nullable VirtualFile file) {
    VirtualFile dir = file != null && !file.isDirectory() ? file.getParent() : file;
    return dir != null ? dir.getPath() : null;
  }

  private void openSessionInDirectory(@NotNull TerminalWidget terminalWidget, @NotNull ShellStartupOptions startupOptions) {
    ModalityState modalityState = ModalityState.stateForComponent(terminalWidget.getComponent());
    TermSize termSize = terminalWidget.getTermSize();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (myProject.isDisposed()) return;
      try {
        ShellStartupOptions baseOptions = startupOptions.builder().initialTermSize(termSize).widget(terminalWidget).build();
        ShellStartupOptions configuredOptions = configureStartupOptions(baseOptions);
        T process = createProcess(configuredOptions);
        TtyConnector connector = createTtyConnector(process);

        ApplicationManager.getApplication().invokeLater(() -> {
          try {
            terminalWidget.connectToTty(connector);
            if (terminalWidget instanceof TerminalWidgetImpl terminalWidgetImpl) {
              terminalWidgetImpl.setStartupOptions(configuredOptions);
            }
          }
          catch (Exception e) {
            printError(terminalWidget, "Cannot create terminal session for " + terminalWidget.getTerminalTitle().buildTitle(), e);
          }
        }, modalityState, myProject.getDisposed());
      }
      catch (Exception e) {
        printError(terminalWidget, "Cannot open " + terminalWidget.getTerminalTitle().buildTitle(), e);
      }
    });
  }

  /**
   * @deprecated use {@link #createTerminalWidget(Disposable, String, boolean)} instead
   * It will be private in future releases.
   */
  @Deprecated(forRemoval = true)
  @ApiStatus.Internal
  public void openSessionInDirectory(@NotNull JBTerminalWidget terminalWidget,
                                     @Nullable String directory) {
    openSessionInDirectory(terminalWidget.asNewWidget(), getStartupOptions(directory));
  }

  private void printError(@NotNull TerminalWidget terminalWidget, @NotNull String errorMessage, @NotNull Exception e) {
    LOG.info(errorMessage, e);
    @Nls StringBuilder message = new StringBuilder();
    message.append("\n");
    message.append(errorMessage).append("\n").append(e.getMessage()).append("\n\n");
    WinPtyException winptyException = ExceptionUtil.findCause(e, WinPtyException.class);
    if (winptyException != null) {
      message.append(winptyException.getMessage()).append("\n\n");
    }
    terminalWidget.writePlainMessage(message.toString());
    terminalWidget.writePlainMessage("\n" + TerminalBundle.message("see.ide.log.error.description", ShowLogAction.getActionName()) + "\n");
    ApplicationManager.getApplication().invokeLater(() -> {
      terminalWidget.setCursorVisible(false);
    }, myProject.getDisposed());
  }

  /**
   * @deprecated use {@link #createProcess(ShellStartupOptions)} instead
   */
  @SuppressWarnings({"removal", "unused"})
  @Deprecated(forRemoval = true)
  public @NotNull T createProcess(@NotNull TerminalProcessOptions options, @Nullable JBTerminalWidget widget) throws ExecutionException {
    return createProcess(options.toStartupOptions());
  }

  /**
   * @deprecated use {@link #createProcess(ShellStartupOptions)} instead
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  protected T createProcess(@Nullable String directory) throws ExecutionException {
    throw new AssertionError("Call createProcess(TerminalProcessOptions)");
  }

  /**
   * @deprecated use {@link #createProcess(ShellStartupOptions)} instead
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  protected T createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
    return createProcess(directory);
  }

  private static class IncompatibleWidgetException extends RuntimeException {
    private IncompatibleWidgetException() {
      super("Please migrate from AbstractTerminalRunner.createTerminalWidget(Disposable, String, boolean) to AbstractTerminalRunner.createShellTerminalWidget");
    }
  }
}
