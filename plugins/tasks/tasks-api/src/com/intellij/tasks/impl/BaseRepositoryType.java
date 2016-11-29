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
package com.intellij.tasks.impl;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class BaseRepositoryType<T extends BaseRepository> extends TaskRepositoryType<T> {

  @NotNull
  public TaskRepositoryEditor createEditor(final T repository, Project project, final Consumer<T> changeListener) {
    return new BaseRepositoryEditor<>(project, repository, changeListener);
  }
}
