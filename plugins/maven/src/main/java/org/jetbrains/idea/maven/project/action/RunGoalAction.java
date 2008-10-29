package org.jetbrains.idea.maven.project.action;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.events.MavenEventsManager;
import org.jetbrains.idea.maven.events.MavenTask;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.runner.MavenRunConfigurationType;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.utils.MavenLog;

public class RunGoalAction extends AnAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  private boolean isEnabled(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return false;

    MavenTask task = MavenEventsManager.getMavenTask(e.getDataContext());
    if (task == null) return false;

    MavenRunnerParameters params = task.createRunnerParameters(MavenProjectsManager.getInstance(project));
    if (params == null) return false;

    return true;
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project == null) return;

    MavenTask task = MavenEventsManager.getMavenTask(e.getDataContext());
    if (task == null) return;

    MavenRunnerParameters params = task.createRunnerParameters(MavenProjectsManager.getInstance(project));
    if (params == null) return;

    try {
      MavenRunConfigurationType.runConfiguration(project, params, e.getDataContext());
    }
    catch (ExecutionException ex) {
      MavenLog.LOG.warn(ex);
    }
  }
}
