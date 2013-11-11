package org.jetbrains.plugins.terminal;

import com.google.common.base.Predicate;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.openapi.Disposable;
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
import com.jediterm.terminal.TtyConnector;
import com.jediterm.terminal.ui.TerminalSession;
import com.jediterm.terminal.ui.TerminalWidget;
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
    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, "Running the terminal", false) {
      public void run(@NotNull final ProgressIndicator indicator) {
        indicator.setText("Running the terminal...");
        try {
          doRun();
        }
        catch (final Exception e) {
          LOG.warn("Error running terminal", e);

          UIUtil.invokeLaterIfNeeded(new Runnable() {

            @Override
            public void run() {
              Messages.showErrorDialog(AbstractTerminalRunner.this.getProject(), e.getMessage(), getTitle());
            }
          });
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

  @NotNull
  public JBTabbedTerminalWidget createTerminalWidget(@NotNull Disposable parent) {
    final JBTerminalSystemSettingsProvider provider = new JBTerminalSystemSettingsProvider();
    JBTabbedTerminalWidget terminalWidget = new JBTabbedTerminalWidget(myProject, provider, new Predicate<TerminalWidget>() {
      @Override
      public boolean apply(TerminalWidget widget) {
        openSession(widget);
        return true;
      }
    }, parent);
    openSession(terminalWidget);
    return terminalWidget;
  }

  private void initConsoleUI(final T process) {
    final Executor defaultExecutor = DefaultRunExecutor.getRunExecutorInstance();
    final DefaultActionGroup toolbarActions = new DefaultActionGroup();
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false);

    

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(actionToolbar.getComponent(), BorderLayout.WEST);

    

    actionToolbar.setTargetComponent(panel);

    ProcessHandler processHandler = createProcessHandler(process);

    final RunContentDescriptor contentDescriptor =
      new RunContentDescriptor(null, processHandler, panel, getTerminalConnectionName(process));

    contentDescriptor.setAutoFocusContent(true);

    toolbarActions.add(createCloseAction(defaultExecutor, contentDescriptor));

    final JBTerminalSystemSettingsProvider provider = new JBTerminalSystemSettingsProvider();
    TerminalWidget widget = new JBTabbedTerminalWidget(myProject, provider, new Predicate<TerminalWidget>() {
      @Override
      public boolean apply(TerminalWidget widget) {
        openSession(widget);
        return true;
      }
    }, contentDescriptor);

    openSession(widget, createTtyConnector(process));

    panel.add(widget.getComponent(), BorderLayout.CENTER);

    showConsole(defaultExecutor, contentDescriptor, widget.getComponent());

    processHandler.startNotify();
  }

  public static void openSession(@NotNull TerminalWidget terminal, @NotNull TtyConnector ttyConnector) {
    TerminalSession session = terminal.createTerminalSession(ttyConnector);
    session.start();
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

  public void openSession(@NotNull TerminalWidget terminalWidget) {
    // Create Server process
    try {
      final T process = createProcess();

      openSession(terminalWidget, createTtyConnector(process));
    }
    catch (Exception e) {
      LOG.error("Can't open terminal session:" + e.getMessage(), e);
    }
  }
}
