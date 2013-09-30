package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.*;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunConfigurationMenu extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    removeAll();

    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());

    final RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    if (settings == null || project == null) return;

    for (final Executor executor : ExecutorRegistry.getInstance().getRegisteredExecutors()) {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executor.getId(), settings.getConfiguration());

      AnAction action = new AnAction(executor.getActionName(), null, executor.getIcon()) {
        @Override
        public void actionPerformed(AnActionEvent event) {
          if (runner == null) return;

          ProgramRunnerUtil.executeConfiguration(project, settings, executor);
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
