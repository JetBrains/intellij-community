package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.NotNull;

public abstract class StudyAfterCheckAction {
  public abstract void run(@NotNull final Project project, @NotNull final Task solvedTask, StudyStatus statusBeforeCheck);
}
