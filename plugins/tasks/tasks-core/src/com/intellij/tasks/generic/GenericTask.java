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
  private final String myDescription;
  private TaskRepository myRepository;

  public GenericTask(final String id, final String description, final TaskRepository repository) {
    myId = id;
    myDescription = description;
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
    return myDescription;
  }

  @Nullable
  @Override
  public String getDescription() {
    return null;
  }

  @NotNull
  @Override
  public Comment[] getComments() {
    return new Comment[0];
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myRepository.getRepositoryType().getIcon();
  }

  @NotNull
  @Override
  public TaskType getType() {
    return TaskType.OTHER;
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return null;
  }

  @Nullable
  @Override
  public Date getCreated() {
    return null;
  }

  @Override
  public boolean isClosed() {
    return false;
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Nullable
  @Override
  public String getIssueUrl() {
    return null;
  }

  @Nullable
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }
}