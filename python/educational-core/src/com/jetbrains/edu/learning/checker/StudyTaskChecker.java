package com.jetbrains.edu.learning.checker;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StudyTaskChecker<T extends Task> {
  @NotNull protected final T myTask;
  @NotNull protected final Project myProject;

  public StudyTaskChecker(@NotNull T task, @NotNull Project project) {
    myTask = task;
    myProject = project;
  }

  public void onTaskSolved(@NotNull String message) {
    ApplicationManager.getApplication().invokeLater(
      () -> StudyCheckUtils.showTestResultPopUp(message, MessageType.INFO.getPopupBackground(), myProject));
  }

  public void onTaskFailed(@NotNull String message) {
    ApplicationManager.getApplication()
      .invokeLater(() -> StudyCheckUtils.showTestResultPopUp(message, MessageType.ERROR.getPopupBackground(), myProject));
  }

  public boolean validateEnvironment() {
    return true;
  }

  public StudyCheckResult check()  {
    return new StudyCheckResult(StudyStatus.Unchecked, "Check for " + myTask.getTaskType() + " task isn't available");
  }

  public StudyCheckResult checkOnRemote(@Nullable StepicUser user)  {
    return new StudyCheckResult(StudyStatus.Unchecked, "Remote check for " + myTask.getTaskType() + " task isn't available");
  }

  public void clearState() {

  }
}
