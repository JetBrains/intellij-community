// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

/**
 * @author Sergey Evdokimov
 */
public class MavenRunConfigurationMenu extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(@NotNull AnActionEvent e) {
    for (AnAction action : getChildActionsOrStubs()) {
      if (action instanceof ExecuteMavenRunConfigurationAction) {
        remove(action);
      }
    }

    final Project project = e.getProject();

    final RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

    if (settings == null || project == null) return;

    Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
    for (int i = executors.length; --i >= 0; ) {
      Executor executor = executors[i];
      if(!executor.isApplicable(project)) continue;
      final ProgramRunner runner = ProgramRunner.getRunner(executor.getId(), settings.getConfiguration());
      AnAction action = new ExecuteMavenRunConfigurationAction(executor, runner != null, settings);
      addAction(action, Constraints.FIRST);
    }

    super.update(e);
  }

  private static class ExecuteMavenRunConfigurationAction extends AnAction {
    private final Executor myExecutor;
    private final boolean myEnabled;
    private final RunnerAndConfigurationSettings mySettings;

    ExecuteMavenRunConfigurationAction(Executor executor,
                                              boolean enabled,
                                              RunnerAndConfigurationSettings settings) {
      super(executor.getActionName(), null, executor.getIcon());
      myExecutor = executor;
      myEnabled = enabled;
      mySettings = settings;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      if (myEnabled) {
        ProgramRunnerUtil.executeConfiguration(mySettings, myExecutor);
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myEnabled);
    }
  }
}
