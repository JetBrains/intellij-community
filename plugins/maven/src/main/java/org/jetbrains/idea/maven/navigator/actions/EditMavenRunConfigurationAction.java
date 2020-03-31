// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

/**
 * @author Sergey Evdokimov
 */
public class EditMavenRunConfigurationAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

    assert settings != null && project != null;

    RunManager.getInstance(project).setSelectedConfiguration(settings);

    EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
    dialog.show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunnerAndConfigurationSettings settings = e.getData(MavenDataKeys.RUN_CONFIGURATION);

    boolean enabled = settings != null && project != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
