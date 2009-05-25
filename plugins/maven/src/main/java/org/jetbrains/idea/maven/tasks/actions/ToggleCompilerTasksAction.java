package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.tasks.MavenCompilerTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ToggleCompilerTasksAction extends MavenToggleAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !getTasks(e).isEmpty();
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return hasTask(getTasksManager(e), getTasks(e).get(0));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    List<MavenCompilerTask> tasks = getTasks(e);
    if (state) {
      addTasks(getTasksManager(e), tasks);
    }
    else {
      removeTasks(getTasksManager(e), tasks);
    }
  }

  protected List<MavenCompilerTask> getTasks(AnActionEvent e) {
    VirtualFile file = getMavenProjectFile(e);
    if (file == null) return Collections.EMPTY_LIST;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    if (goals == null || goals.isEmpty()) return Collections.EMPTY_LIST;

    List<MavenCompilerTask> result = new ArrayList<MavenCompilerTask>();
    for (String each : goals) {
      result.add(new MavenCompilerTask(file.getPath(), each));
    }
    return result;
  }

  protected abstract boolean hasTask(MavenTasksManager manager, MavenCompilerTask task);

  protected abstract void addTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks);

  protected abstract void removeTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks);

  private MavenTasksManager getTasksManager(AnActionEvent e) {
    return MavenTasksManager.getInstance(getProject(e));
  }
}
