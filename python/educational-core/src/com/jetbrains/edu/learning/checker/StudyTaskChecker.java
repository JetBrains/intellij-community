package com.jetbrains.edu.learning.checker;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.NotNull;

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
}
