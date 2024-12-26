// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

public class MavenTasksManagerState {
  public Set<MavenCompilerTask> beforeCompileTasks = new TreeSet<>();
  public Set<MavenCompilerTask> afterCompileTasks = new TreeSet<>();
  public Set<MavenCompilerTask> afterRebuildTask = new TreeSet<>();
  public Set<MavenCompilerTask> beforeRebuildTask = new TreeSet<>();

  public @NotNull Set<MavenCompilerTask> getTasks(@NotNull MavenTasksManager.Phase phase) {
    return switch (phase) {
      case AFTER_COMPILE -> afterCompileTasks;
      case BEFORE_COMPILE -> beforeCompileTasks;
      case AFTER_REBUILD -> afterRebuildTask;
      case BEFORE_REBUILD -> beforeRebuildTask;
    };
  }
}
