package com.intellij.tasks.mantis;

import com.intellij.tasks.Comment;
import com.intellij.tasks.Task;
import com.intellij.tasks.TaskRepository;
import com.intellij.tasks.TaskType;
import com.intellij.tasks.mantis.model.IssueData;
import com.intellij.tasks.mantis.model.IssueHeaderData;
import com.intellij.tasks.mantis.model.IssueNoteData;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import icons.TasksCoreIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;

public class MantisTask extends Task {
  private final String myId;
  private final String mySummary;
  private final String myDescription;
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
      myComments = ContainerUtil.map2Array(data.getNotes(), Comment.class, (Function<IssueNoteData, Comment>)data1 -> new Comment() {
        @Override
        public String getText() {
          return data1.getText();
        }

        @Nullable
        @Override
        public String getAuthor() {
          return data1.getReporter().getName();
        }

        @Nullable
        @Override
        public Date getDate() {
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
    return myComments;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TasksCoreIcons.Mantis;
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
