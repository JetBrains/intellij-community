package org.jetbrains.plugins.terminal;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.ui.UIUtil;
import com.jediterm.emulator.TtyConnector;
import org.jetbrains.annotations.NotNull;

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
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Connecting to terminal", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Connecting to terminal...");
        try {
          doRun();
        }
        catch (Exception e) {
          LOG.warn("Error running terminal", e);
          Messages.showErrorDialog(AbstractTerminalRunner.this.getProject(), getTitle(), e.getMessage());
        }
      }
    });
  }

  private void doRun() {
    // Create Server process
    try {
      final T process = createProcess();

      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          initConsoleUI(process);
        }
      });
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  protected abstract T createProcess() throws ExecutionException;

  protected abstract ProcessHandler createProcessHandler(T process);

  private void initConsoleUI(final T process) {
    final Executor defaultExecutor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);

    final JBTerminal term = new JBTerminal();

    term.setTtyConnector(createTtyConnector(process));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);

    panel.add(term, BorderLayout.CENTER);
    term.start();

    actionToolbar.setTargetComponent(panel);

    ProcessHandler processHandler = createProcessHandler(process);

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(null, processHandler, panel, getTerminalConnectionName(process));

    contentDescriptor.setAutoFocusContent(true);

    toolbarActions.add(createCloseAction(defaultExecutor, contentDescriptor));

    showConsole(defaultExecutor, contentDescriptor, term.getTerminalPanel());

    processHandler.startNotify();
  }

  protected abstract String getTerminalConnectionName(T process);

  protected abstract TtyConnector createTtyConnector(T process);

  protected AnAction createCloseAction(final Executor defaultExecutor, final RunContentDescriptor myDescriptor) {
    return new CloseAction(defaultExecutor, myDescriptor, myProject);
  }


  protected void showConsole(Executor defaultExecutor, RunContentDescriptor myDescriptor, final Component toFocus) {
    // Show in run toolwindow
    ExecutionManager.getInstance(myProject).getContentManager().showRunContent(defaultExecutor, myDescriptor);

// Request focus
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(defaultExecutor.getId());
    window.activate(new Runnable() {
      public void run() {
        IdeFocusManager.getInstance(myProject).requestFocus(toFocus, true);
      }
    });
  }

  protected Project getProject() {
    return myProject;
  }
}
