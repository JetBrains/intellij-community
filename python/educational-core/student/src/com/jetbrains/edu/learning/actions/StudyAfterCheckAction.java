package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

public abstract class StudyAfterCheckAction extends AnAction {
  public abstract void run(@NotNull final Project project, @NotNull final Task solvedTask);
}
