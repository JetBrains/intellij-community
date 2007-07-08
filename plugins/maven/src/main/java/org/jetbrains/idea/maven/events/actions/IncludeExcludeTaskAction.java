package org.jetbrains.idea.maven.events.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.events.MavenEventsState;
import org.jetbrains.idea.maven.events.MavenTask;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public abstract class IncludeExcludeTaskAction extends IncludeExcludeAction<MavenTask> {
  protected MavenTask getElement(AnActionEvent e) {
    return MavenEventsState.getMavenTask(e.getDataContext());
  }

  private MavenEventsHandler getEventsHandler(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    return project != null ? project.getComponent(MavenEventsHandler.class) : null;
  }

  protected Collection<MavenTask> getCollection(AnActionEvent e) {
    final MavenEventsHandler eventsHandler = getEventsHandler(e);
    return eventsHandler != null ? getCollection(eventsHandler) : null;
  }

  protected void onToggle(final AnActionEvent e, final MavenTask element) {
    final MavenEventsHandler eventsHandler = getEventsHandler(e);
    if(eventsHandler!=null){
      eventsHandler.updateTaskShortcuts(element);
    }
  }

  protected abstract Collection<MavenTask> getCollection(MavenEventsHandler eventsHandler);
}
