package com.intellij.tasks.mantis;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import icons.TasksIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class MantisTask extends Task {
  private final String myId;
  private final String mySummary;
  private final Date myTime;
  private String myProjectName;
  private MantisRepository myRepository;

  public MantisTask(final String id, final String summary, MantisProject project, MantisRepository repository, final Date time) {
    myId = id;
    mySummary = summary;
    myTime = time;
    myProjectName = !MantisProject.ALL_PROJECTS.equals(project) ? project.getName() : null;
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
    return TasksIcons.Mantis;
  }

  @NotNull
  @Override
  public TaskType getType() {
    return TaskType.OTHER;
  }

  @Nullable
  @Override
  public Date getUpdated() {
    return myTime;
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

  @Override
  public String getIssueUrl() {
    return myRepository.getUrl() + "/view.php?id=" + getId();
  }

  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }

  @Nullable
  @Override
  public String getProject() {
    return myProjectName;
  }

  @NotNull
  @Override
  public String getNumber() {
    return getId();
  }
}
