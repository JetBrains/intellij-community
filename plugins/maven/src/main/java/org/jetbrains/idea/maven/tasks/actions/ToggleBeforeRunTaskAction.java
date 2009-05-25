package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

import java.util.List;

public class ToggleBeforeRunTaskAction extends MavenToggleAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getTaskDesc(e) != null;
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    Pair<MavenProject, String> desc = getTaskDesc(e);
    for (MavenBeforeRunTask each : getRunManager(e).getBeforeRunTasks(MavenBeforeRunTasksProvider.TASK_ID, true)) {
      if (each.isEnabled() && each.isFor(desc.first, desc.second)) return true;
    }
    return false;
  }

  @Override
  public void setSelected(final AnActionEvent e, boolean state) {
    Pair<MavenProject, String> desc = getTaskDesc(e);
    new SelectBeforeRunTaskDialog(getProject(e), desc.first, desc.second).show();
  }

  protected Pair<MavenProject, String> getTaskDesc(AnActionEvent e) {
    VirtualFile file = getMavenProjectFile(e);
    if (file == null) return null;

    MavenProject mavenProject = getProjectsManager(e).findProject(file);
    if (mavenProject == null) return null;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    if (goals == null || goals.size() != 1) return null;

    return Pair.create(mavenProject, goals.get(0));
  }

  private RunManagerEx getRunManager(AnActionEvent e) {
    return RunManagerEx.getInstanceEx(getProject(e));
  }
}
