package com.intellij.tasks.generic;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class GenericTask extends Task {
  private final String myId;
  private final String mySummary;
  private String myDescription;
  private Date myUpdated;
  private Date myCreated;
  private String myIssueUrl;
  private final TaskRepository myRepository;
  private boolean myClosed;

  public GenericTask(final String id, final String summary, final TaskRepository repository) {
    myId = id;
    mySummary = summary;
    myRepository = repository;
  }

  @NotNull
  @Override
  public String getId() {
    return myId;
  }

  @NotNull
  @Override
  public String getSummary() {
    return mySummary;
  }

  @Nullable
  @Override
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  public Comment[] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myRepository.getIcon();
  }

  @NotNull
  @Override
  public TaskType getType() {
    return TaskType.OTHER;
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return myUpdated;
  }

  @Nullable
  @Override
  public Date getCreated() {
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

  @Nullable
  @Override
  public String getIssueUrl() {
    return myIssueUrl;
  }

  @Nullable
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }

  public void setIssueUrl(@Nullable String issueUrl) {
    myIssueUrl = issueUrl;
  }

  public void setCreated(@Nullable Date created) {
    myCreated = created;
  }

  public void setUpdated(@Nullable Date updated) {
    myUpdated = updated;
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
  }

  public void setClosed(boolean closed) {
    myClosed = closed;
  }
}