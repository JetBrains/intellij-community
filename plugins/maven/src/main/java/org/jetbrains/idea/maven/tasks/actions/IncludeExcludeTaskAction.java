package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.tasks.MavenTask;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class IncludeExcludeTaskAction extends IncludeExcludeAction<MavenTask> {
  protected MavenTask getElement(AnActionEvent e) {
    return MavenTasksManager.getMavenTask(e.getDataContext());
  }

  private MavenTasksManager getEventsHandler(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    return project != null ? project.getComponent(MavenTasksManager.class) : null;
  }

  protected Collection<MavenTask> getCollection(AnActionEvent e) {
    final MavenTasksManager eventsHandler = getEventsHandler(e);
    return eventsHandler != null ? getCollection(eventsHandler) : null;
  }

  protected void onToggle(final AnActionEvent e, final MavenTask element) {
    final MavenTasksManager eventsHandler = getEventsHandler(e);
    if(eventsHandler!=null){
      eventsHandler.updateTaskShortcuts(element);
    }
  }

  protected abstract Collection<MavenTask> getCollection(MavenTasksManager eventsHandler);
}
