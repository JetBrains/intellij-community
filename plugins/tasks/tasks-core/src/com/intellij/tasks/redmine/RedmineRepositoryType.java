// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.redmine;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class RedmineRepositoryType extends BaseRepositoryType<RedmineRepository> {

  @Override
  public @NotNull String getName() {
    return "Redmine";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Redmine;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new RedmineRepository(this);
  }

  @Override
  public Class<RedmineRepository> getRepositoryClass() {
    return RedmineRepository.class;
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(RedmineRepository repository,
                                                    Project project,
                                                    Consumer<? super RedmineRepository> changeListener) {
    return new RedmineRepositoryEditor(project, repository, changeListener);
  }
}
