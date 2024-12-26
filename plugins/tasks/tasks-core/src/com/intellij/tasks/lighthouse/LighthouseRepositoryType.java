// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.lighthouse;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dennis.Ushakov
 */
public class LighthouseRepositoryType extends BaseRepositoryType<LighthouseRepository> {
  @Override
  public @NotNull String getName() {
    return "Lighthouse";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Lighthouse;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new LighthouseRepository(this);
  }

  @Override
  public Class<LighthouseRepository> getRepositoryClass() {
    return LighthouseRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.RESOLVED, TaskState.OTHER);
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(LighthouseRepository repository,
                                                    Project project,
                                                    Consumer<? super LighthouseRepository> changeListener) {
    return new LighthouseRepositoryEditor(project, repository, changeListener);
  }
}
