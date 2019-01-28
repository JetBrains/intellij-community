package org.jetbrains.plugins.terminal;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.jediterm.terminal.RequestOrigin;
import com.jediterm.terminal.TerminalStarter;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.TerminalSession;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * @author traff
 */
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
    Disposer.register(project, mySettingsProvider);
  }

  public void run() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Running the terminal", false) {
      @Override
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Running the terminal...");
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
      final T process = createProcess(null);

      UIUtil.invokeLaterIfNeeded(() -> initConsoleUI(process));
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  protected abstract T createProcess(@Nullable String directory) throws ExecutionException;

  @ApiStatus.Experimental
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

    JBTerminalWidget terminalWidget = new JBTerminalWidget(myProject, mySettingsProvider, parent);
    Runnable openSession = () -> openSessionForFile(terminalWidget, currentWorkingDirectory);
    if (deferSessionUntilFirstShown) {
      UiNotifyConnector.doWhenFirstShown(terminalWidget, openSession);
    }
    else {
      openSession.run();
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
      new RunContentDescriptor(null, processHandler, panel, getTerminalConnectionName(process));

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

  public static void createAndStartSession(@NotNull JBTerminalWidget terminal, @NotNull TtyConnector ttyConnector) {
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
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(defaultExecutor, myDescriptor);

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
        // Create Server process
        final T process = createProcess(directory, terminalWidget.getCommandHistoryFilePath());
        TtyConnector connector = createTtyConnector(process);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Initial resize to " + size);
        }
        if (size != null) {
          // Resize ASAP once the process started.
          // Even though it will be resized in invokeLater, it takes some time until invokeLater is executed.
          // Sometimes it's enough to have cropped output, if the output is restricted by the terminal width.
          try {
            TerminalStarter.resizeTerminal(terminalWidget.getTerminal(), connector, size, RequestOrigin.User);
          }
          catch (Exception e) {
            LOG.warn("Cannot resize right after creation, process.isAlive: " + process.isAlive(), e);
          }
        }

        ApplicationManager.getApplication().invokeLater(() -> {
          try {
            terminalWidget.createTerminalSession(connector);
            terminalWidget.start();
            terminalWidget.getComponent().revalidate();
            terminalWidget.notifyStarted();
          }
          catch (RuntimeException e) {
            showCannotOpenTerminalDialog(e);
          }
        }, modalityState);
      }
      catch (Exception e) {
        LOG.info("Cannot open " + runningTargetName(), e);
        ApplicationManager.getApplication().invokeLater(() -> showCannotOpenTerminalDialog(e), modalityState);
      }
    });
  }

  private void showCannotOpenTerminalDialog(@NotNull Throwable e) {
    Messages.showErrorDialog(e.getMessage(), "Can't Open " + runningTargetName());
  }
}
