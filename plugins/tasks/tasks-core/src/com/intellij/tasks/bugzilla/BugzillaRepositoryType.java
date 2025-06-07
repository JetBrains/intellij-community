// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.bugzilla;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskRepositoryType;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Mikhail Golubev
 */
public class BugzillaRepositoryType extends TaskRepositoryType<BugzillaRepository> {
  @Override
  public @NotNull String getName() {
    return "Bugzilla";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Bugzilla;
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(BugzillaRepository repository, Project project, Consumer<? super BugzillaRepository> changeListener) {
    return new BugzillaRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public @NotNull TaskRepository createRepository() {
    return new BugzillaRepository(this);
  }

  @Override
  public Class<BugzillaRepository> getRepositoryClass() {
    return BugzillaRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    // UNCONFIRMED, CONFIRMED, IN_PROGRESS, RESOLVED (resolution=FIXED)
    return EnumSet.of(TaskState.SUBMITTED, TaskState.OPEN, TaskState.IN_PROGRESS, TaskState.RESOLVED);
  }
}

