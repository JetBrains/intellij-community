// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.ArrayList;
import java.util.List;

public final class MavenRunConfigurationMenu extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    for (AnAction action : getChildActionsOrStubs()) {
      if (action instanceof ExecuteMavenRunConfigurationAction) {
        remove(action);
      }
    }

    Project project = e.getProject();
    RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

    if (settings == null || project == null) {
      return;
    }

    @SuppressWarnings("DuplicatedCode")
    final List<Executor> executors = new ArrayList<>();
    for (final Executor executor: Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      if (executor instanceof ExecutorGroup) {
        executors.addAll(((ExecutorGroup<?>)executor).childExecutors());
      }
      else {
        executors.add(executor);
      }
    }
    for (int i = executors.size(); --i >= 0; ) {
      Executor executor = executors.get(i);
      if (!executor.isApplicable(project)) {
        continue;
      }
      ProgramRunner<?> runner = ProgramRunner.getRunner(executor.getId(), settings.getConfiguration());
      AnAction action = new ExecuteMavenRunConfigurationAction(executor, runner != null, settings);
      addAction(action, Constraints.FIRST);
    }

    super.update(e);
  }

  private static final class ExecuteMavenRunConfigurationAction extends AnAction {
    private final Executor myExecutor;
    private final boolean myEnabled;
    private final RunnerAndConfigurationSettings mySettings;

    ExecuteMavenRunConfigurationAction(Executor executor, boolean enabled, RunnerAndConfigurationSettings settings) {
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
