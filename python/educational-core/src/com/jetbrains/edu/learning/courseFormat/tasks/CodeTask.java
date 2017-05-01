package com.jetbrains.edu.learning.courseFormat.tasks;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.checker.StudyCheckResult;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;

public class CodeTask extends Task {
  @SuppressWarnings("unused") //used for deserialization
  public CodeTask() {}

  public CodeTask(@NotNull final String name) {
    super(name);
  }

  @Override
  public String getTaskType() {
    return "code";
  }

  @Override
  public StudyTaskChecker getChecker(@NotNull Project project) {
    return new StudyTaskChecker<CodeTask>(this, project) {
      @Override
      public void onTaskFailed(@NotNull String message) {
        super.onTaskFailed("Wrong solution");
        StudyCheckUtils.showTestResultsToolWindow(myProject, message);
      }

      @Override
      public StudyCheckResult checkOnRemote(@NotNull StepicUser user) {
        return EduAdaptiveStepicConnector.checkCodeTask(myProject, myTask, user);
      }
    };
  }
}
