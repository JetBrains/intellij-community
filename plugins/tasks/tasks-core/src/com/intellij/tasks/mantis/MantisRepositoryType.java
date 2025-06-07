// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.mantis;

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
public class MantisRepositoryType extends BaseRepositoryType<MantisRepository> {

  @Override
  public @NotNull String getName() {
    return "Mantis";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Mantis;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new MantisRepository(this);
  }

  @Override
  public @NotNull Class<MantisRepository> getRepositoryClass() {
    return MantisRepository.class;
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(final MantisRepository repository,
                                                    final Project project,
                                                    final Consumer<? super MantisRepository> changeListener) {
    return new MantisRepositoryEditor(project, repository, changeListener);
  }
}
