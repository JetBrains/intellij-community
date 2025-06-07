// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.terminal;

import com.intellij.execution.Executor;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.ide.actions.ShowLogAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jediterm.core.util.TermSize;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.windows.winpty.WinPtyException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.block.TerminalWidgetImpl;
import org.jetbrains.plugins.terminal.block.ui.TerminalUiUtils;

import java.awt.*;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.jetbrains.plugins.terminal.TerminalStartupKt.initStartupMomentIfNeeded;
import static org.jetbrains.plugins.terminal.TerminalStartupKt.logCommonStartupInfo;

public abstract class AbstractTerminalRunner<T extends Process> {
  private static final Logger LOG = Logger.getInstance(AbstractTerminalRunner.class);
  protected final @NotNull Project myProject;
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

  /**
   * @deprecated use {@link #createTerminalWidget(Disposable, VirtualFile, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  public @NotNull JBTerminalWidget createTerminalWidget(@NotNull Disposable parent, @Nullable VirtualFile currentWorkingDirectory) {
    return createTerminalWidget(parent, getParentDirectoryPath(currentWorkingDirectory), true);
  }

  /**
   * @deprecated use {@link AbstractTerminalRunner#createTerminalWidget(Disposable, String, boolean)} instead
   */
  @Deprecated(forRemoval = true)
  protected @NotNull JBTerminalWidget createTerminalWidget(@NotNull Disposable parent,
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
      UiNotifyConnector.doWhenFirstShown(terminalWidget.getComponent(), () -> openSession(terminalWidget, startupOptions));
    }
    else {
      openSession(terminalWidget, startupOptions);
    }
  }

  public @Nullable String getCurrentWorkingDir(@Nullable TerminalTabState state) {
    return state != null ? state.myWorkingDirectory : null;
  }

  public @Nullable @NlsContexts.TabTitle String getDefaultTabTitle() {
    return null;
  }

  /**
   * @deprecated use {@link #getDefaultTabTitle()} instead
   */
  @SuppressWarnings("unused")
  @Deprecated(forRemoval = true)
  protected String getTerminalConnectionName(T process) {
    return getDefaultTabTitle();
  }

  public abstract @NotNull TtyConnector createTtyConnector(@NotNull T process);

  protected @NotNull Project getProject() {
    return myProject;
  }

  /**
   * @deprecated use {@link #getDefaultTabTitle()} instead
   */
  @Deprecated(forRemoval = true)
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

  /**
   * @return true if reworked block terminal (gen2) should be used instead of the Classic Terminal.
   */
  @ApiStatus.Internal
  public boolean isGenTwoTerminalEnabled() {
    return false;
  }

  private static @Nullable String getParentDirectoryPath(@Nullable VirtualFile file) {
    VirtualFile dir = file != null && !file.isDirectory() ? file.getParent() : file;
    return dir != null ? dir.getPath() : null;
  }

  @ApiStatus.Internal
  @RequiresEdt(generateAssertion = false)
  protected void openSession(@NotNull TerminalWidget terminalWidget, @NotNull ShellStartupOptions startupOptions) {
    doOpenSession(terminalWidget, initStartupMomentIfNeeded(startupOptions.builder()).widget(terminalWidget).build());
  }

  @RequiresEdt(generateAssertion = false)
  private void doOpenSession(@NotNull TerminalWidget terminalWidget, @NotNull ShellStartupOptions startupOptions) {
    ModalityState modalityState = ModalityState.stateForComponent(terminalWidget.getComponent());
    CheckedDisposable widgetDisposable = Disposer.newCheckedDisposable(terminalWidget);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      if (myProject.isDisposed() || widgetDisposable.isDisposed()) return;
      ShellStartupOptions configuredOptions = configureStartupOptions(startupOptions);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (widgetDisposable.isDisposed()) return;
        JBTerminalWidget jediTermWidget = JBTerminalWidget.asJediTermWidget(terminalWidget);
        if (jediTermWidget instanceof ShellTerminalWidget shellWidget) {
          shellWidget.setStartupOptions(configuredOptions);
        }
        CompletableFuture<TermSize> initialTermSizeFuture = awaitTermSize(terminalWidget, configuredOptions);
        initialTermSizeFuture.whenComplete((initialTermSize, initialTermSizeError) -> {
          if (myProject.isDisposed() || widgetDisposable.isDisposed()) return;
          if (initialTermSize == null) {
            LOG.warn("Cannot get terminal size from component, defaulting to 80x24", initialTermSizeError);
            initialTermSize = new TermSize(80, 24);
          }
          TerminalStartupMoment startupMoment = Objects.requireNonNull(startupOptions.getStartupMoment$intellij_terminal());
          Duration durationBetweenStartupAndComponentResized = startupMoment.elapsedNow();
          TermSize resultInitialTermSize = initialTermSize;
          ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (myProject.isDisposed() || widgetDisposable.isDisposed()) return;
            try {
              T process = createProcess(configuredOptions.builder().initialTermSize(resultInitialTermSize).build());
              TtyConnector connector = createTtyConnector(process);
              Duration durationBetweenStartupAndConnectorCreated = startupMoment.elapsedNow();
              logCommonStartupInfo(connector, process, durationBetweenStartupAndComponentResized, durationBetweenStartupAndConnectorCreated);
              ApplicationManager.getApplication().invokeLater(() -> {
                if (widgetDisposable.isDisposed()) return;
                try {
                  terminalWidget.connectToTty(connector, resultInitialTermSize);
                }
                catch (Exception e) {
                  printError(terminalWidget, "Cannot create terminal session for " + terminalWidget.getTerminalTitle().buildTitle(), e);
                }
              }, modalityState, myProject.getDisposed());
            }
            catch (Throwable e) {
              printError(terminalWidget, "Cannot open " + terminalWidget.getTerminalTitle().buildTitle(), e);
            }
          });
        });
      }, modalityState, myProject.getDisposed());
    });
  }

  private static @NotNull CompletableFuture<TermSize> awaitTermSize(@NotNull TerminalWidget terminalWidget,
                                                                    @NotNull ShellStartupOptions configuredOptions) {
    if (terminalWidget instanceof TerminalWidgetImpl terminalWidgetImpl) {
      return terminalWidgetImpl.initialize(configuredOptions);
    }
    var future = TerminalUiUtils.INSTANCE.getComponentSizeInitializedFuture(terminalWidget.getComponent());
    TerminalUiUtils.INSTANCE.cancelFutureByTimeout(future, 2000, terminalWidget);
    return future.thenApply(v -> {
      return terminalWidget.getTermSize();
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
    openSession(terminalWidget.asNewWidget(), getStartupOptions(directory));
  }

  private void printError(@NotNull TerminalWidget terminalWidget, @NotNull String errorMessage, @NotNull Throwable e) {
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

  /**
   * @deprecated {@link TerminalToolWindowManager} instead
   */
  @Deprecated(forRemoval = true)
  public void run() {
    TerminalToolWindowManager.getInstance(myProject).createNewSession(this);
  }

  /**
   * @deprecated use {@link RunContentManager#showRunContent(Executor, RunContentDescriptor)} instead
   */
  @Deprecated(forRemoval = true)
  protected void showConsole(@NotNull Executor defaultExecutor, @NotNull RunContentDescriptor myDescriptor, Component ignoredToFocus) {
    RunContentManager.getInstance(myProject).showRunContent(defaultExecutor, myDescriptor);
  }

  /**
   * @deprecated unused API, just remove overridden method
   */
  @Deprecated(forRemoval = true)
  protected @NotNull ProcessHandler createProcessHandler(T ignoredProcess) {
    return new NopProcessHandler();
  }

  private static final class IncompatibleWidgetException extends RuntimeException {
    private IncompatibleWidgetException() {
      super("Please migrate from AbstractTerminalRunner.createTerminalWidget(Disposable, String, boolean) to AbstractTerminalRunner.createShellTerminalWidget");
    }
  }
}
