// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.gitlab;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Mikhail Golubev
 */
public class GitlabRepositoryType extends BaseRepositoryType<GitlabRepository>{
  @Override
  public @NotNull String getName() {
    return "Gitlab";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Gitlab;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new GitlabRepository(this);
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(GitlabRepository repository,
                                                    Project project,
                                                    Consumer<? super GitlabRepository> changeListener) {
    return new GitlabRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public Class<GitlabRepository> getRepositoryClass() {
    return GitlabRepository.class;
  }
}
