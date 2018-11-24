package com.jetbrains.edu.learning.courseFormat.tasks;

import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.actions.StudyCheckAction;
import com.jetbrains.edu.learning.checker.StudyCheckResult;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.checker.StudyTaskChecker;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      public StudyCheckResult checkOnRemote(@Nullable StepicUser user) {
        if (user == null) {
          return new StudyCheckResult(StudyStatus.Unchecked, StudyCheckAction.FAILED_CHECK_LAUNCH);
        }
        return EduAdaptiveStepicConnector.checkCodeTask(myProject, myTask, user);
      }
    };
  }
}
