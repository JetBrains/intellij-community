/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.statistics.MavenActionsUsagesCollector;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

/**
 * @author Sergey Evdokimov
 */
public class EditMavenRunConfigurationAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    MavenActionsUsagesCollector.trigger(project, this, e);
    RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    assert settings != null && project != null;

    RunManager.getInstance(project).setSelectedConfiguration(settings);

    EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
    dialog.show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    boolean enabled = settings != null && project != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
