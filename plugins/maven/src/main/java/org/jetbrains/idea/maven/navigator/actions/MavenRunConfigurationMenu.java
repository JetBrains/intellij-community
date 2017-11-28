/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.*;
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
  public void update(AnActionEvent e) {
    for (AnAction action : getChildActionsOrStubs()) {
      if (action instanceof ExecuteMavenRunConfigurationAction) {
        remove(action);
      }
    }

    final Project project = e.getProject();

    final RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    if (settings == null || project == null) return;

    Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
    for (int i = executors.length; --i >= 0; ) {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(executors[i].getId(), settings.getConfiguration());
      AnAction action = new ExecuteMavenRunConfigurationAction(executors[i], runner != null, settings);
      addAction(action, Constraints.FIRST);
    }

    super.update(e);
  }

  private static class ExecuteMavenRunConfigurationAction extends AnAction {
    private final Executor myExecutor;
    private final boolean myEnabled;
    private final RunnerAndConfigurationSettings mySettings;

    public ExecuteMavenRunConfigurationAction(Executor executor,
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
      super.update(e);
      e.getPresentation().setEnabled(myEnabled);
    }
  }
}
