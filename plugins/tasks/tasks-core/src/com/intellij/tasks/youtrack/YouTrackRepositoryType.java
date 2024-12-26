// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.youtrack;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.TaskState;
import com.intellij.tasks.config.TaskRepositoryEditor;
import com.intellij.tasks.impl.BaseRepositoryType;
import com.intellij.util.Consumer;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author Dmitry Avdeev
 */
public class YouTrackRepositoryType extends BaseRepositoryType<YouTrackRepository> {

  @Override
  public @NotNull String getName() {
    return "YouTrack";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Youtrack;
  }

  @Override
  public @Nullable String getAdvertiser() {
    return TaskBundle.message("more.features.available.in.youtrack.plugin");
  }

  @Override
  public @NotNull YouTrackRepository createRepository() {
    return new YouTrackRepository(this);
  }

  @Override
  public @NotNull Class<YouTrackRepository> getRepositoryClass() {
    return YouTrackRepository.class;
  }

  @Override
  public EnumSet<TaskState> getPossibleTaskStates() {
    return EnumSet.of(TaskState.IN_PROGRESS, TaskState.RESOLVED);
  }

  @Override
  public @NotNull TaskRepositoryEditor createEditor(YouTrackRepository repository, Project project, Consumer<? super YouTrackRepository> changeListener) {
    return new YouTrackRepositoryEditor(project, repository, changeListener);
  }

  @Override
  public int getSortOrder() {
    return 1;
  }
}
