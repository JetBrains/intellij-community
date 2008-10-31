package org.jetbrains.idea.maven.project.action;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.runner.MavenRunConfigurationType;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.List;

public class RunGoalAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  private boolean isEnabled(AnActionEvent e) {
    return checkOrPerform(e, false);
  }

  public void actionPerformed(AnActionEvent e) {
    checkOrPerform(e, true);
  }

  private boolean checkOrPerform(AnActionEvent e, boolean perform) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return false;

    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (file == null || !MavenConstants.POM_XML.equals(file.getName())) return false;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS_KEY);
    if (goals == null || goals.isEmpty()) return false;

    if (!perform) return true;

    try {
      MavenRunnerParameters params = new MavenRunnerParameters(
        true, file.getParent().getPath(), goals, MavenProjectsManager.getInstance(project).getActiveProfiles());
      MavenRunConfigurationType.runConfiguration(project, params, e.getDataContext());
    }
    catch (ExecutionException ex) {
      MavenLog.LOG.warn(ex);
    }

    return true;
  }
}
