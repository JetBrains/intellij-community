/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.tasks;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.TreeSet;

public class MavenTasksManagerState {
  public Set<MavenCompilerTask> beforeCompileTasks = new TreeSet<>();
  public Set<MavenCompilerTask> afterCompileTasks = new TreeSet<>();
  public Set<MavenCompilerTask> afterRebuildTask = new TreeSet<>();
  public Set<MavenCompilerTask> beforeRebuildTask = new TreeSet<>();

  @NotNull
  public Set<MavenCompilerTask> getTasks(@NotNull MavenTasksManager.Phase phase) {
    switch (phase) {
      case AFTER_COMPILE:
        return afterCompileTasks;
      case BEFORE_COMPILE:
        return beforeCompileTasks;
      case AFTER_REBUILD:
        return afterRebuildTask;
      case BEFORE_REBUILD:
        return beforeRebuildTask;
      default:
        throw new RuntimeException();
    }
  }
}
