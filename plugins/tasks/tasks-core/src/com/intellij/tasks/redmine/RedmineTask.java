package com.intellij.tasks.redmine;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.redmine.model.RedmineIssue;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public class RedmineTask extends Task {
  private final RedmineIssue myIssue;
  private final RedmineRepository myRepository;

  public RedmineTask(@NotNull RedmineIssue issue, @NotNull RedmineRepository repository) {
    myIssue = issue;
    myRepository = repository;
  }

  @NotNull
  @Override
  public String getId() {
    return String.valueOf(myIssue.getId());
  }

  @NotNull
  @Override
  public String getSummary() {
    return myIssue.getSubject();
  }

  @Nullable
  @Override
  public String getDescription() {
    return myIssue.getDescription();
  }

  @NotNull
  @Override
  public Comment[] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Redmine;
  }

  @NotNull
  @Override
  public TaskType getType() {
    // TODO: precise mapping
    return TaskType.BUG;
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return myIssue.getUpdated();
  }

  @Nullable
  @Override
  public Date getCreated() {
    return myIssue.getCreated();
  }

  @Override
  public boolean isClosed() {
    String name = myIssue.getStatus().getName();
    return name.equals("Closed") || name.equals("Resolved");
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Nullable
  @Override
  public String getIssueUrl() {
    return myRepository.getRestApiUrl("issues", getId());
  }
}
