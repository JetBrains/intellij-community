package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.*;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.MavenUtil;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunConfigurationMenu extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    removeAll();

    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

    final MavenRunConfiguration runConfiguration = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    if (runConfiguration == null || project == null) return;

    for (final Executor executor : ExecutorRegistry.getInstance().getRegisteredExecutors()) {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), runConfiguration);

      AnAction action = new AnAction(executor.getActionName(), null, executor.getIcon()) {
        @Override
        public void actionPerformed(AnActionEvent event) {
          if (runner == null) return;

          RunManagerImpl runManager = (RunManagerImpl)RunManager.getInstance(project);

          RunnerAndConfigurationSettings settings = new RunnerAndConfigurationSettingsImpl(runManager, runConfiguration, false);
          ExecutionEnvironment env = new ExecutionEnvironment(executor, runner, settings, project);

          try {
            runner.execute(env, null);
          }
          catch (ExecutionException e) {
            MavenUtil.showError(project, "Failed to execute Maven goal", e);
          }
        }

        @Override
        public void update(AnActionEvent e) {
          super.update(e);
          e.getPresentation().setEnabled(runner != null);
        }
      };

      addAction(action);
    }


    super.update(e);
  }
}
