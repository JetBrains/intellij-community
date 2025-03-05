// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.pivotal;

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
public class PivotalTrackerRepositoryType extends BaseRepositoryType<PivotalTrackerRepository> {

  @Override
  public @NotNull String getName() {
    return "PivotalTracker";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Pivotal;
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new PivotalTrackerRepository(this);
  }

  @Override
  public Class<PivotalTrackerRepository> getRepositoryClass() {
    return PivotalTrackerRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.RESOLVED, TaskState.OTHER, TaskState.IN_PROGRESS);
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(PivotalTrackerRepository repository,
                                                    Project project,
                                                    Consumer<? super PivotalTrackerRepository> changeListener) {
    return new PivotalTrackerRepositoryEditor(project, repository, changeListener);
  }
}
