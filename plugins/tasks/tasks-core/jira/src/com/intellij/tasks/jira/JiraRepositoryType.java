// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.jira;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dmitry Avdeev
 */
public class JiraRepositoryType extends BaseRepositoryType<JiraRepository> {

  public JiraRepositoryType() {
  }

  @Override
  public @NotNull String getName() {
    return "JIRA";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Jira;
  }

  @Override
  public @NotNull JiraRepository createRepository() {
    return new JiraRepository(this);
  }

  @Override
  public @NotNull Class<JiraRepository> getRepositoryClass() {
    return JiraRepository.class;
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(JiraRepository repository,
                                                    Project project,
                                                    Consumer<? super JiraRepository> changeListener) {
    return new JiraRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.OPEN, TaskState.IN_PROGRESS, TaskState.REOPENED, TaskState.RESOLVED);
  }
}

