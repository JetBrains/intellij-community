package org.jetbrains.idea.maven.project.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.runner.MavenRunConfigurationType;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.List;

public class RunBuildAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && checkOrPerform(e, false);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    checkOrPerform(e, true);
  }

  private boolean checkOrPerform(AnActionEvent e, boolean perform) {
    VirtualFile file = MavenActionUtils.getMavenProjectFile(e);
    if (file == null) return false;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    if (goals == null || goals.isEmpty()) return false;

    if (!perform) return true;

    try {
      MavenRunnerParameters params = new MavenRunnerParameters(
        true, file.getParent().getPath(), goals, MavenActionUtils.getProjectsManager(e).getActiveProfiles());
      MavenRunConfigurationType.runConfiguration(MavenActionUtils.getProject(e), params, e.getDataContext());
    }
    catch (ExecutionException ex) {
      MavenLog.LOG.warn(ex);
    }

    return true;
  }
}
