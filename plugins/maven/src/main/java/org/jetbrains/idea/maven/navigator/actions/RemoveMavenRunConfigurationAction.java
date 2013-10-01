package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import javax.swing.*;

/**
 * @author Sergey Evdokimov
 */
public class RemoveMavenRunConfigurationAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    RunnerAndConfigurationSettings settings = MavenDataKeys.RUN_CONFIGURATION.getData(e.getDataContext());

    if (settings == null || project == null) return;

    int res = JOptionPane
      .showConfirmDialog(JOptionPane.getRootFrame(), "Delete \"" + settings.getName() + "\"?", "Confirmation", JOptionPane.YES_NO_OPTION);

    if (res == JOptionPane.YES_OPTION) {
      ((RunManagerEx)RunManager.getInstance(project)).removeConfiguration(settings);
    }
  }
}
