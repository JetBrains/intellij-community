// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull TaskRepositoryEditor createEditor(final T repository, Project project, final Consumer<? super T> changeListener) {
    return new BaseRepositoryEditor<>(project, repository, changeListener);
  }
}
