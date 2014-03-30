package com.intellij.tasks.gitlab;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.gitlab.model.GitlabIssue;
import com.intellij.tasks.gitlab.model.GitlabProject;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

/**
 * @author Mikhail Golubev
 */
public class GitlabTask extends Task {
  private final GitlabIssue myIssue;
  private final GitlabRepository myRepository;
  private final GitlabProject myProject;

  public GitlabTask(@NotNull GitlabRepository respository,
                    @NotNull GitlabProject project,
                    @NotNull GitlabIssue issue) {
    myRepository = respository;
    myIssue = issue;
    myProject = project;
  }

  @NotNull
  @Override
  public String getId() {
    return String.valueOf(myIssue.getId());
  }

  @NotNull
  @Override
  public String getSummary() {
    return myIssue.getTitle();
  }

  @Nullable
  @Override
  public String getDescription() {
    return null;
  }

  @NotNull
  @Override
  public Comment[] getComments() {
    return Comment.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksIcons.Gitlab;
  }

  @NotNull
  @Override
  public TaskType getType() {
    return TaskType.BUG;
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return myIssue.getUpdatedAt();
  }

  @Nullable
  @Override
  public Date getCreated() {
    return myIssue.getCreatedAt();
  }

  @Override
  public boolean isClosed() {
    return myIssue.getState().equals("closed");
  }

  @Override
  public boolean isIssue() {
    return true;
  }

  @Nullable
  @Override
  public String getIssueUrl() {
    return myProject.getWebUrl() + "/issues/" + myIssue.getLocalId();
  }
}
