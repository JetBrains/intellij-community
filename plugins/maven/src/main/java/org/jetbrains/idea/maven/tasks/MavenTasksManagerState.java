package org.jetbrains.idea.maven.tasks;

import java.util.Set;
import java.util.TreeSet;

public class MavenTasksManagerState {
  public Set<MavenCompilerTask> beforeCompileTasks = new TreeSet<MavenCompilerTask>();
  public Set<MavenCompilerTask> afterCompileTasks = new TreeSet<MavenCompilerTask>();
}
