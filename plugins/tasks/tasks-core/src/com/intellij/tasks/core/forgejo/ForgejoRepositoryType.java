// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.core.forgejo;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

public class ForgejoRepositoryType extends BaseRepositoryType<ForgejoRepository> {
  @Override
  public @NotNull String getName() {
    return "Forgejo";
  }

  @Override
  public @NotNull Icon getIcon() {
    // TODO: replace with Forgejo icon
    return AllIcons.Actions.Stub;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new ForgejoRepository(this);
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(ForgejoRepository repository,
                                                    Project project,
                                                    Consumer<? super ForgejoRepository> changeListener) {
    return new ForgejoRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public Class<ForgejoRepository> getRepositoryClass() {
    return ForgejoRepository.class;
  }
}
