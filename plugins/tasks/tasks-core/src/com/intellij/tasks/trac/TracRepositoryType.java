// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.trac;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class TracRepositoryType extends BaseRepositoryType<TracRepository> {

  @Override
  public @NotNull String getName() {
    return "Trac";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Trac;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new TracRepository(this);
  }

  @Override
  public Class<TracRepository> getRepositoryClass() {
    return TracRepository.class;
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(TracRepository repository,
                                                    Project project,
                                                    Consumer<? super TracRepository> changeListener) {
    return new TracRepositoryEditor(project, repository, changeListener);
  }
}
