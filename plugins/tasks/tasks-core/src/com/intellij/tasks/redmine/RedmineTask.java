package com.intellij.tasks.redmine;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.redmine.model.RedmineIssue;
import com.intellij.tasks.redmine.model.RedmineProject;
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
  /**
   * Only human-readable project name is sent with issue. Because project's identifier is more suited
   * for commit messages, it has to be extracted from cached projects. The same approach is used in
   * {@link com.intellij.tasks.gitlab.GitlabRepository}.
   */
  private final RedmineProject myProject;

  public RedmineTask(@NotNull RedmineRepository repository, @NotNull RedmineIssue issue) {
    myIssue = issue;
    myRepository = repository;
    RedmineProject project = null;
    for (RedmineProject p : repository.getProjects()) {
      if (issue.getProject() != null && p.getId() == issue.getProject().getId()) {
        project = p;
        break;
      }
    }
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

  @NotNull
  @Override
  public String getNumber() {
    return getId();
  }

  @Nullable
  @Override
  public String getProject() {
    return myProject == null ? null : myProject.getIdentifier();
  }

  @Nullable
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }
}
