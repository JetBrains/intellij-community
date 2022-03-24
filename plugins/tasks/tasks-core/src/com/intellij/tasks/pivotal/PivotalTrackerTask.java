// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.pivotal;

import com.intellij.openapi.util.IconLoader;
import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.impl.TaskUtil;
import com.intellij.tasks.pivotal.model.PivotalTrackerStory;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.Date;

public final class PivotalTrackerTask extends Task {
  private final @NotNull PivotalTrackerRepository myRepository;
  private final @NotNull PivotalTrackerStory myStory;

  public PivotalTrackerTask(@NotNull PivotalTrackerRepository repository, @NotNull PivotalTrackerStory story) {
    myRepository = repository;
    myStory = story;
  }

  @Override
  public @NotNull TaskRepository getRepository() {
    return myRepository;
  }

  @Override
  public @NotNull String getId() {
    return myStory.getProjectId() + "-" + myStory.getId();
  }

  @Override
  public @NotNull String getPresentableId() {
    return "#" + myStory.getId();
  }

  @Override
  public @NotNull String getNumber() {
    return myStory.getId();
  }

  @Override
  public @NotNull String getSummary() {
    return myStory.getName();
  }

  @Override
  public @Nullable String getDescription() {
    return myStory.getDescription();
  }

  @Override
  public Comment @NotNull [] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @Override
  public @NotNull Icon getIcon() {
    return IconLoader.getIcon(getCustomIcon(), PivotalTrackerRepository.class.getClassLoader());
  }

  @Override
  public @NotNull TaskType getType() {
    return TaskType.OTHER;
  }

  @Override
  public @Nullable Date getUpdated() {
    return TaskUtil.parseDate(myStory.getUpdatedAt());
  }

  @Override
  public @Nullable Date getCreated() {
    return TaskUtil.parseDate(myStory.getCreatedAt());
  }

  @Override
  public boolean isClosed() {
    return ArrayUtil.contains(myStory.getCurrentState(), "accepted", "delivered", "finished");
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Override
  public @NotNull String getIssueUrl() {
    return myRepository.getUrl() + "/story/show/" + myStory.getId();
  }

  @Override
  public @NotNull String getCustomIcon() {
    return "icons/pivotal/" + myStory.getStoryType() + ".png";
  }

  @TestOnly
  public @NotNull PivotalTrackerStory getStory() {
    return myStory;
  }
}
