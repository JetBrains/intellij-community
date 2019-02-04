// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public class PivotalTrackerTask extends Task {
  @NotNull private final PivotalTrackerRepository myRepository;
  @NotNull private final PivotalTrackerStory myStory;

  public PivotalTrackerTask(@NotNull PivotalTrackerRepository repository, @NotNull PivotalTrackerStory story) {
    myRepository = repository;
    myStory = story;
  }

  @Nullable
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }

  @NotNull
  @Override
  public String getId() {
    return myStory.getProjectId() + "-" + myStory.getId();
  }

  @NotNull
  @Override
  public String getPresentableId() {
    return "#" + myStory.getId();
  }

  @NotNull
  @Override
  public String getNumber() {
    return myStory.getId();
  }

  @NotNull
  @Override
  public String getSummary() {
    return myStory.getName();
  }

  @Nullable
  @Override
  public String getDescription() {
    return myStory.getDescription();
  }

  @NotNull
  @Override
  public Comment[] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return IconLoader.getIcon(getCustomIcon(), PivotalTrackerRepository.class);
  }

  @NotNull
  @Override
  public TaskType getType() {
    return TaskType.OTHER;
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return TaskUtil.parseDate(myStory.getUpdatedAt());
  }

  @Nullable
  @Override
  public Date getCreated() {
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

  @Nullable
  @Override
  public String getIssueUrl() {
    return myRepository.getUrl() + "/story/show/" + myStory.getId();
  }

  @NotNull
  @Override
  public String getCustomIcon() {
    return "/icons/pivotal/" + myStory.getStoryType() + ".png";
  }

  @TestOnly
  @NotNull
  public PivotalTrackerStory getStory() {
    return myStory;
  }
}
