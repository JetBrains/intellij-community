package org.jetbrains.idea.maven.events.actions;

import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.events.MavenTask;

import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public class BeforeCompileAction extends IncludeExcludeTaskAction {
  protected Collection<MavenTask> getCollection(MavenEventsHandler eventsHandler) {
    return eventsHandler.getState().beforeCompile;
  }
}
