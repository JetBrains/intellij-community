// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.mantis;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.mantis.model.IssueData;
import com.intellij.tasks.mantis.model.IssueHeaderData;
import com.intellij.util.containers.ContainerUtil;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class MantisTask extends Task {
  private final String myId;
  private final @Nls String mySummary;
  private final @Nls String myDescription;
  private final Date myUpdated;
  private final Date myCreated;
  private final boolean myClosed;
  private final String myProjectName;
  private final MantisRepository myRepository;
  private final Comment[] myComments;

  public MantisTask(@NotNull IssueData data, @NotNull MantisRepository repository) {
    myRepository = repository;
    myId = String.valueOf(data.getId());
    mySummary = data.getSummary();
    myDescription = data.getDescription();
    myProjectName = data.getProject() == null ? null : data.getProject().getName();
    myClosed = data.getStatus().getId().intValue() >= 90;
    myCreated = data.getDate_submitted().getTime();
    myUpdated = data.getLast_updated().getTime();

    if (data.getNotes() == null) {
      myComments = Comment.EMPTY_ARRAY;
    }
    else {
      myComments = ContainerUtil.map2Array(data.getNotes(), Comment.class, data1 -> new Comment() {
        @Override
        public String getText() {
          return data1.getText();
        }

        @Override
        public @Nullable String getAuthor() {
          return data1.getReporter().getName();
        }

        @Override
        public @NotNull Date getDate() {
          return data1.getDate_submitted().getTime();
        }
      });
    }
  }

  public MantisTask(@NotNull IssueHeaderData header, @NotNull MantisRepository repository) {
    myRepository = repository;
    myId = String.valueOf(header.getId());
    mySummary = header.getSummary();
    myProjectName = null;
    myClosed = header.getStatus().intValue() >= 90;
    myDescription = null; // unavailable from header
    myCreated = null; // unavailable from header
    myUpdated = header.getLast_updated().getTime();
    myComments = Comment.EMPTY_ARRAY;
  }

  @Override
  public @NotNull String getId() {
    return myId;
  }

  @Override
  public @NotNull String getSummary() {
    return mySummary;
  }

  @Override
  public @Nullable String getDescription() {
    return myDescription;
  }

  @Override
  public Comment @NotNull [] getComments() {
    return myComments;
  }

  @Override
  public @NotNull Icon getIcon() {
    return TasksCoreIcons.Mantis;
  }

  @Override
  public @NotNull TaskType getType() {
    return TaskType.OTHER;
  }

  @Override
  public @Nullable Date getUpdated() {
    return myUpdated;
  }

  @Override
  public @Nullable Date getCreated() {
    return myCreated;
  }

  @Override
  public boolean isClosed() {
    return myClosed;
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Override
  public String getIssueUrl() {
    return myRepository.getUrl() + "/view.php?id=" + getId();
  }

  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }

  @Override
  public @Nullable String getProject() {
    return myProjectName;
  }

  @Override
  public @NotNull String getNumber() {
    return getId();
  }
}
