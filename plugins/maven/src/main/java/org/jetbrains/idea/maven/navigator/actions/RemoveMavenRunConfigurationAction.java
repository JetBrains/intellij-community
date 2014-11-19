/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

/**
 * @author Sergey Evdokimov
 */
public class RemoveMavenRunConfigurationAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    assert settings != null && project != null;

    int res = Messages.showYesNoDialog(project, "Delete \"" + settings.getName() + "\"?", "Confirmation", Messages.getQuestionIcon());
    if (res == Messages.YES) {
      ((RunManagerEx)RunManager.getInstance(project)).removeConfiguration(settings);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    boolean enabled = settings != null && project != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
