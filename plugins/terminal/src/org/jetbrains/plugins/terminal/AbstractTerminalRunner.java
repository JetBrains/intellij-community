// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.TerminalSession;
import com.pty4j.windows.WinPtyException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public abstract class AbstractTerminalRunner<T extends Process> {
  private static final Logger LOG = Logger.getInstance(AbstractTerminalRunner.class);
  @NotNull
  protected final Project myProject;
  private final JBTerminalSystemSettingsProvider mySettingsProvider;

  public JBTerminalSystemSettingsProvider getSettingsProvider() {
    return mySettingsProvider;
  }

  public AbstractTerminalRunner(@NotNull Project project) {
    myProject = project;
    mySettingsProvider = new JBTerminalSystemSettingsProvider();
  }

  public void run() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, TerminalBundle.message("progress.title.running.terminal"), false) {
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
      final T process = createProcess(new TerminalProcessOptions(null, null, null), null);

      UIUtil.invokeLaterIfNeeded(() -> initConsoleUI(process));
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  public @NotNull T createProcess(@NotNull TerminalProcessOptions options, @Nullable JBTerminalWidget widget) throws ExecutionException {
    return createProcess(options.getWorkingDirectory(), null);
  }

  /**
   * @deprecated use {@link #createProcess(TerminalProcessOptions, JBTerminalWidget)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public T createProcess(@Nullable String directory) throws ExecutionException {
    throw new AssertionError("Call createProcess(TerminalProcessOptions, JBTerminalWidget)");
  }

  /**
   * @deprecated use {@link #createProcess(TerminalProcessOptions, JBTerminalWidget)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  protected T createProcess(@Nullable String directory, @Nullable String commandHistoryFilePath) throws ExecutionException {
    return createProcess(directory);
  }

  protected abstract ProcessHandler createProcessHandler(T process);

  @NotNull
  public JBTerminalWidget createTerminalWidget(@NotNull Disposable parent, @Nullable VirtualFile currentWorkingDirectory) {
    return createTerminalWidget(parent, currentWorkingDirectory, true);
  }

  @NotNull
  protected JBTerminalWidget createTerminalWidget(@NotNull Disposable parent,
                                                  @Nullable VirtualFile currentWorkingDirectory,
                                                  boolean deferSessionUntilFirstShown) {

    return createTerminalWidget(parent,
                                (terminalWidget) -> openSessionForFile(terminalWidget, currentWorkingDirectory),
                                true);
  }

  @NotNull
  protected JBTerminalWidget createTerminalWidget(@NotNull Disposable parent,
                                                  @Nullable String currentWorkingDirectory,
                                                  boolean deferSessionUntilFirstShown) {

    return createTerminalWidget(parent,
                                (terminalWidget) -> openSessionInDirectory(terminalWidget, currentWorkingDirectory),
                                true);
  }

  private JBTerminalWidget createTerminalWidget(@NotNull Disposable parent,
                                                @NotNull Consumer<JBTerminalWidget> openSession,
                                                boolean deferSessionUntilFirstShown) {
    JBTerminalWidget terminalWidget = new ShellTerminalWidget(myProject, mySettingsProvider, parent);
    if (deferSessionUntilFirstShown) {
      UiNotifyConnector.doWhenFirstShown(terminalWidget, () -> openSession.accept(terminalWidget));
    }
    else {
      openSession.accept(terminalWidget);
    }
    return terminalWidget;
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
      new RunContentDescriptor(null, processHandler, panel, getTerminalConnectionName(process)); //NON-NLS

    contentDescriptor.setAutoFocusContent(true);

    toolbarActions.add(createCloseAction(defaultExecutor, contentDescriptor));

    final JBTerminalSystemSettingsProvider provider = new JBTerminalSystemSettingsProvider();
    JBTerminalWidget widget = new JBTerminalWidget(myProject, provider, contentDescriptor);

    createAndStartSession(widget, createTtyConnector(process));

    panel.add(widget.getComponent(), BorderLayout.CENTER);

    showConsole(defaultExecutor, contentDescriptor, widget.getComponent());

    processHandler.startNotify();
  }

  public void openSession(@NotNull JBTerminalWidget terminal) {
    openSessionInDirectory(terminal, null);
  }

  public @Nullable String getCurrentWorkingDir(@Nullable TerminalTabState state) {
    String dir = state != null ? state.myWorkingDirectory : null;
    VirtualFile result = dir == null ? null : LocalFileSystem.getInstance().findFileByPath(dir);
    return result == null ? null : result.getPath();
  }

  private static void createAndStartSession(@NotNull JBTerminalWidget terminal, @NotNull TtyConnector ttyConnector) {
    TerminalSession session = terminal.createTerminalSession(ttyConnector);
    session.start();
  }

  protected abstract String getTerminalConnectionName(T process);

  protected abstract TtyConnector createTtyConnector(T process);

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

  public abstract String runningTargetName();

  public void openSessionForFile(@NotNull JBTerminalWidget terminalWidget, @Nullable VirtualFile file) {
    openSessionInDirectory(terminalWidget, getParentDirectoryPath(file));
  }

  @Nullable
  private static String getParentDirectoryPath(@Nullable VirtualFile file) {
    VirtualFile dir = file != null && !file.isDirectory() ? file.getParent() : file;
    return dir != null ? dir.getPath() : null;
  }

  public void openSessionInDirectory(@NotNull JBTerminalWidget terminalWidget,
                                     @Nullable String directory) {
    ModalityState modalityState = ModalityState.stateForComponent(terminalWidget.getComponent());
    Dimension size = terminalWidget.getTerminalPanel().getTerminalSizeFromComponent();

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        T process = createProcess(new TerminalProcessOptions(directory,
                                                             size != null ? size.width : null,
                                                             size != null ? size.height : null), terminalWidget);
        TtyConnector connector = createTtyConnector(process);

        ApplicationManager.getApplication().invokeLater(() -> {
          try {
            terminalWidget.createTerminalSession(connector);
          }
          catch (Exception e) {
            printError(terminalWidget, "Cannot create terminal session for " + runningTargetName(), e);
          }
          try {
            terminalWidget.start();
            terminalWidget.getComponent().revalidate();
            terminalWidget.notifyStarted();
          }
          catch (RuntimeException e) {
            printError(terminalWidget, "Cannot open " + runningTargetName(), e);
          }
        }, modalityState);
      }
      catch (Exception e) {
        printError(terminalWidget, "Cannot open " + runningTargetName(), e);
      }
    });
  }

  private void printError(@NotNull JBTerminalWidget terminalWidget, @NotNull String errorMessage, @NotNull Exception e) {
    LOG.info(errorMessage, e);
    @Nls StringBuilder message = new StringBuilder();
    if (terminalWidget.getTerminal().getCursorX() > 1) {
      message.append("\n");
    }
    message.append(errorMessage).append("\n").append(e.getMessage()).append("\n\n");
    WinPtyException winptyException = ExceptionUtil.findCause(e, WinPtyException.class);
    if (winptyException != null) {
      message.append(winptyException.getMessage()).append("\n\n");
    }
    terminalWidget.writePlainMessage(message.toString());
    terminalWidget.writePlainMessage("\n" + TerminalBundle.message("see.ide.log.error.description", ShowLogAction.getActionName()) + "\n");
    ApplicationManager.getApplication().invokeLater(() -> {
      terminalWidget.getTerminalPanel().setCursorVisible(false);
    }, myProject.getDisposed());
  }
}
