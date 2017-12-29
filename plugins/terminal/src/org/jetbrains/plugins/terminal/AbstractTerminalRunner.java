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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.TerminalSession;
import com.jediterm.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * @author traff
 */
public abstract class AbstractTerminalRunner<T extends Process> {
  private static final Logger LOG = Logger.getInstance(AbstractTerminalRunner.class.getName());
  @NotNull
  protected final Project myProject;

  public AbstractTerminalRunner(@NotNull Project project) {
    myProject = project;
  }

  public void run() {
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Running the terminal", false) {
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

  protected abstract ProcessHandler createProcessHandler(T process);

  @NotNull
  public JBTabbedTerminalWidget createTerminalWidget(@NotNull Disposable parent) {
    final JBTerminalSystemSettingsProvider provider = new JBTerminalSystemSettingsProvider();
    JBTabbedTerminalWidget terminalWidget = new JBTabbedTerminalWidget(myProject, provider, widget -> {
      openSessionInDirectory(widget.getFirst(), widget.getSecond());
      return true;
    }, parent);
    openSessionForFile(terminalWidget, TerminalView.getInstance(myProject).getFileToOpen());
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
    TerminalWidget widget = new JBTabbedTerminalWidget(myProject, provider, widget1 -> {
      openSessionInDirectory(widget1.getFirst(), widget1.getSecond());
      return true;
    }, contentDescriptor);

    createAndStartSession(widget, createTtyConnector(process));

    panel.add(widget.getComponent(), BorderLayout.CENTER);

    showConsole(defaultExecutor, contentDescriptor, widget.getComponent());

    processHandler.startNotify();
  }

  public void openSession(@NotNull TerminalWidget terminal) {
    openSessionInDirectory(terminal, null);
  }

  public static void createAndStartSession(@NotNull TerminalWidget terminal, @NotNull TtyConnector ttyConnector) {
    TerminalSession session = terminal.createTerminalSession(ttyConnector);

    TerminalView.recordUsage(ttyConnector);

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

  public void openSessionForFile(@NotNull TerminalWidget terminalWidget, @Nullable VirtualFile file) {
    openSessionInDirectory(terminalWidget, getParentDirectoryPath(file));
  }

  @Nullable
  private static String getParentDirectoryPath(@Nullable VirtualFile file) {
    VirtualFile dir = file != null && !file.isDirectory() ? file.getParent() : file;
    return dir != null ? dir.getPath() : null;
  }

  private void openSessionInDirectory(@NotNull TerminalWidget terminalWidget, @Nullable String directory) {
    ModalityState modalityState = ModalityState.stateForComponent(terminalWidget.getComponent());

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        // Create Server process
        final T process = createProcess(directory);

        ApplicationManager.getApplication().invokeLater(() -> {
          try {
            createAndStartSession(terminalWidget, createTtyConnector(process));
            terminalWidget.getComponent().revalidate();
          }
          catch (RuntimeException e) {
            showCannotOpenTerminalDialog(e);
          }
        }, modalityState);
      }
      catch (Exception e) {
        ApplicationManager.getApplication().invokeLater(() -> showCannotOpenTerminalDialog(e), modalityState);
      }
    });
  }

  private void showCannotOpenTerminalDialog(@NotNull Throwable e) {
    Messages.showErrorDialog(e.getMessage(), "Can't Open " + runningTargetName());
  }
}
