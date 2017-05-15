package com.jetbrains.edu.learning.courseFormat.tasks;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.checker.StudyCheckResult;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TheoryTask extends Task {
  @SuppressWarnings("unused") //used for deserialization
  public TheoryTask() {}

  public TheoryTask(@NotNull final String name) {
    super(name);
  }

  @Override
  public String getTaskType() {
    return "theory";
  }

  @Override
  public StudyTaskChecker getChecker(@NotNull Project project) {
    return new StudyTaskChecker<TheoryTask>(this, project) {
      @Override
      public void onTaskSolved(@NotNull String message) {
      }

      @Override
      public StudyCheckResult check() {
        return new StudyCheckResult(StudyStatus.Solved, "");
      }

      @Override
      public StudyCheckResult checkOnRemote(@Nullable StepicUser user) {
        return check();
      }
    };
  }
}
