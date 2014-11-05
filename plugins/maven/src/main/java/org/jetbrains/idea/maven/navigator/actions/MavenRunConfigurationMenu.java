package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunConfigurationMenu extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    for (AnAction action : getChildActionsOrStubs()) {
      if (action instanceof ExecuteMavenRunConfigurationAction) {
        remove(action);
      }
    }

    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());

    final RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    if (settings == null || project == null) return;

    Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
    for (int i = executors.length; --i >= 0; ) {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executors[i].getId(), settings.getConfiguration());
      AnAction action = new ExecuteMavenRunConfigurationAction(executors[i], runner != null, project, settings);
      addAction(action, Constraints.FIRST);
    }

    super.update(e);
  }

  private static class ExecuteMavenRunConfigurationAction extends AnAction {
    private final Executor myExecutor;
    private final boolean myEnabled;
    private final Project myProject;
    private final RunnerAndConfigurationSettings mySettings;

    public ExecuteMavenRunConfigurationAction(Executor executor,
                                              boolean enabled,
                                              Project project,
                                              RunnerAndConfigurationSettings settings) {
      super(executor.getActionName(), null, executor.getIcon());
      myExecutor = executor;
      myEnabled = enabled;
      myProject = project;
      mySettings = settings;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      if (myEnabled) {
        ProgramRunnerUtil.executeConfiguration(myProject, mySettings, myExecutor);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myEnabled);
    }
  }
}
