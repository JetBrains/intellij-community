package com.jetbrains.edu.learning.courseFormat.tasks;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import org.jetbrains.annotations.NotNull;

public class TaskWithSubtasks extends Task {
  private int myActiveSubtaskIndex = 0;
  @SerializedName("last_subtask_index")
  @Expose private int myLastSubtaskIndex = 0;

  public TaskWithSubtasks() {}

  public TaskWithSubtasks(@NotNull final String name) {
    super(name);
  }

  public TaskWithSubtasks(Task task) {
    setName(task.getName());
    setIndex(task.getIndex());
    setStatus(task.getStatus());
    setStepId(task.getStepId());
    taskFiles = task.getTaskFiles();
    setText(task.getText());
    testsText = task.getTestsText();
    taskTexts = task.getTaskTexts();
    setLesson(task.getLesson());
    setUpdateDate(task.getUpdateDate());
  }

  public int getActiveSubtaskIndex() {
    return myActiveSubtaskIndex;
  }

  public void setActiveSubtaskIndex(int activeSubtaskIndex) {
    myActiveSubtaskIndex = activeSubtaskIndex;
  }

  public int getLastSubtaskIndex() {
    return myLastSubtaskIndex;
  }

  public void setLastSubtaskIndex(int lastSubtaskIndex) {
    myLastSubtaskIndex = lastSubtaskIndex;
  }

  public void setStatus(StudyStatus status) {
    super.setStatus(status);
    if (status == StudyStatus.Solved && getActiveSubtaskIndex() != getLastSubtaskIndex()) {
      if (myStatus == StudyStatus.Failed) {
        myStatus = StudyStatus.Unchecked;
      }
    }
  }

  public String getTaskType() {
    return "subtasks";
  }
}
