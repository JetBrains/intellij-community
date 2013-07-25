/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.tasks;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumSet;

/**
 * The main extension point for issue tracking integration.
 *
 * @author Dmitry Avdeev
 */
public abstract class TaskRepositoryType<T extends TaskRepository> {

  public static final ExtensionPointName<TaskRepositoryType> EP_NAME = new ExtensionPointName<TaskRepositoryType>("com.intellij.tasks.repositoryType");

  @NotNull
  public abstract String getName();

  @NotNull
  public abstract Icon getIcon();

  @Nullable
  public String getAdvertiser() { return null; }

  @NotNull
  public abstract TaskRepositoryEditor createEditor(T repository, Project project, Consumer<T> changeListener);

  @NotNull
  public abstract TaskRepository createRepository();

  public abstract Class<T> getRepositoryClass();

  /**
   * @return states that can be set by {@link TaskRepository#setTaskState(Task, TaskState)}
   */
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.noneOf(TaskState.class);
  }
}
