package org.jetbrains.plugins.ipnb;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class IpnbConsole extends ConsoleViewImpl {
  private final KillableColoredProcessHandler myProcess;

  public IpnbConsole(@NotNull final Project project, @NotNull final KillableColoredProcessHandler processHandler) {
    super(project, false);
    myProcess = processHandler;

    final Executor executor = DefaultRunExecutor.getRunExecutorInstance();
    final DefaultActionGroup actions = new DefaultActionGroup();

    final JComponent consolePanel = createConsolePanel(actions);
    final RunContentDescriptor descriptor = new RunContentDescriptor(this, myProcess, consolePanel, "IPython Notebook");

    Disposer.register(this, descriptor);
    actions.add(new StopAction());
    ExecutionManager.getInstance(getProject()).getContentManager().showRunContent(executor, descriptor);
  }

  private JComponent createConsolePanel(ActionGroup actions) {
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.add(getComponent(), BorderLayout.CENTER);
    panel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, actions, false).getComponent(), BorderLayout.WEST);
    return panel;
  }

  private class StopAction extends AnAction implements DumbAware {
    public StopAction() {
      super("Stop", "Stop", AllIcons.Actions.Suspend);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myProcess.isProcessTerminated()) return;
      myProcess.destroyProcess();
      UnixProcessManager.sendSigIntToProcessTree(myProcess.getProcess());
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setVisible(true);
      e.getPresentation().setEnabled(!myProcess.isProcessTerminated());
    }
  }
}
