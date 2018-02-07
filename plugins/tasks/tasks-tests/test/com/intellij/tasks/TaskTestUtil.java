package com.intellij.tasks;

import com.intellij.tasks.impl.TaskUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Date;
import java.util.List;

import static junit.framework.Assert.assertTrue;

/**
 * @author Mikhail Golubev
 */
public class TaskTestUtil {
  public static void  assertTasksEqual(@NotNull Task t1, @NotNull Task t2) {
    assertTrue(TaskUtil.tasksEqual(t1, t2));
  }

  public static void  assertTasksEqual(@NotNull List<? extends Task> t1, @NotNull List<? extends Task> t2) {
    assertTrue(TaskUtil.tasksEqual(t1, t2));
  }

  public static void  assertTasksEqual(@NotNull Task[] t1, @NotNull Task[] t2) {
    assertTrue(TaskUtil.tasksEqual(t1, t2));
  }

  /**
   * Auxiliary builder class to simplify comparison of server responses parsing results.
   *
   * @see #assertTasksEqual(Task, Task)
   */
  public static class TaskBuilder extends Task {
    private final String myId;
    private final String mySummary;
    private final TaskRepository myRepository;
    private String myDescription;
    private String myIssueUrl;
    private Comment[] myComments = Comment.EMPTY_ARRAY;
    private Icon myIcon;
    private TaskType myType = TaskType.OTHER;
    private TaskState myState;
    private Date myCreated;
    private Date myUpdated;
    private boolean myClosed = false;
    private boolean myIssue = true;

    public TaskBuilder(String id, String summary, TaskRepository repository) {
      myId = id;
      mySummary = summary;
      myRepository = repository;
    }

    public TaskBuilder withDescription(@Nullable String description) {
      myDescription = description;
      return this;
    }

    public TaskBuilder withIssueUrl(@Nullable String issueUrl) {
      myIssueUrl = issueUrl;
      return this;
    }

    public TaskBuilder withComments(@NotNull Comment... comments) {
      myComments = comments;
      return this;
    }

    public TaskBuilder withClosed(boolean isClosed) {
      myClosed = isClosed;
      return this;
    }

    public TaskBuilder withIssue(boolean isIssue) {
      myIssue = isIssue;
      return this;
    }

    public TaskBuilder withUpdated(@Nullable Date updated) {
      myUpdated = updated;
      return this;
    }

    public TaskBuilder withUpdated(@NotNull String updated) {
      return withUpdated(TaskUtil.parseDate(updated));
    }

    public TaskBuilder withCreated(@Nullable Date created) {
      myCreated = created;
      return this;
    }

    public TaskBuilder withCreated(@NotNull String created) {
      return withCreated(TaskUtil.parseDate(created));
    }

    public TaskBuilder withType(@NotNull TaskType type) {
      myType = type;
      return this;
    }

    public TaskBuilder withState(@Nullable TaskState state) {
      myState = state;
      return this;
    }

    public TaskBuilder withIcon(@Nullable Icon icon) {
      myIcon = icon;
      return this;
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
      return myIcon == null? myRepository.getIcon() : myIcon;
    }

    @NotNull
    @Override
    public TaskType getType() {
      return myType;
    }

    @Nullable
    @Override
    public TaskState getState() {
      return myState;
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
      return myIssue;
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
  }


}
