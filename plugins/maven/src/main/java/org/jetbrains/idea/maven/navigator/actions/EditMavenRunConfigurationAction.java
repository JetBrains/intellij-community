package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

/**
 * @author Sergey Evdokimov
 */
public class EditMavenRunConfigurationAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    assert settings != null && project != null;

    RunManager.getInstance(project).setSelectedConfiguration(settings);

    EditConfigurationsDialog dialog = new EditConfigurationsDialog(project);
    dialog.show();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    boolean enabled = settings != null && project != null;
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
