package org.jetbrains.idea.maven.tasks.actions;

import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.tasks.MavenTask;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public class AfterCompileAction extends IncludeExcludeTaskAction {
  protected Collection<MavenTask> getCollection(MavenTasksManager eventsHandler) {
    return eventsHandler.getState().afterCompile;
  }
}