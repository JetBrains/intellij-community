package com.jetbrains.edu.learning.checker;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.tasks.ChoiceTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StudyCheckTask extends com.intellij.openapi.progress.Task.Backgroundable {

  private static final Logger LOG = Logger.getInstance(StudyCheckTask.class);
  private final Project myProject;
  protected final StudyState myStudyState;
  protected final Task myTask;
  protected final VirtualFile myTaskDir;
  protected final StudyTaskManager myTaskManger;
  private final StudyStatus myStatusBeforeCheck;
  private final Ref<Boolean> myCheckInProcess;
  private final Process myTestProcess;
  private final String myCommandLine;
  private static final String FAILED_CHECK_LAUNCH = "Failed to launch checking";

  public StudyCheckTask(Project project, StudyState studyState, Ref<Boolean> checkInProcess, Process testProcess, String commandLine) {
    super(project, "Checking Task");
    myProject = project;
    myStudyState = studyState;
    myCheckInProcess = checkInProcess;
    myTestProcess = testProcess;
    myCommandLine = commandLine;
    myTask = studyState.getTask();
    myTaskDir = studyState.getTaskDir();
    myTaskManger = StudyTaskManager.getInstance(myProject);
    myStatusBeforeCheck = myTask.getStatus();
  }

  @Override
  public void onSuccess() {
    StudyUtils.updateToolWindows(myProject);
    StudyCheckUtils.drawAllPlaceholders(myProject, myTask);
    ProjectView.getInstance(myProject).refresh();
    clearState();
  }

  protected void clearState() {
    EduUtils.deleteWindowDescriptions(myTask, myTaskDir);
    myCheckInProcess.set(false);
  }

  @Override
  public void onCancel() {
    myTask.setStatus(myStatusBeforeCheck);
    clearState();
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    if (course != null) {
      if (course.isAdaptive()) {
        checkForAdaptiveCourse(indicator);
      }
      else {
        checkForEduCourse(indicator);
      }
      runAfterTaskCheckedActions();
    }
  }

  private void checkForEduCourse(@NotNull ProgressIndicator indicator) {
    final StudyTestsOutputParser.TestsOutput testsOutput = getTestOutput(indicator);
    if (testsOutput != null) {
      if (testsOutput.isSuccess()) {
        onTaskSolved(testsOutput.getMessage());
      }
      else {
        onTaskFailed(testsOutput.getMessage());
      }
      final Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (course != null && EduNames.STUDY.equals(course.getCourseMode())) {
        StepicUser user = StudySettings.getInstance().getUser();
        if (user != null) {
          EduStepicConnector.postSolution(myTask, testsOutput.isSuccess(), myProject);
        }
      }
    }
  }

  @Nullable
  private StudyTestsOutputParser.TestsOutput getTestOutput(@NotNull ProgressIndicator indicator) {
    final CapturingProcessHandler handler = new CapturingProcessHandler(myTestProcess, null, myCommandLine);
    final ProcessOutput output = handler.runProcessWithProgressIndicator(indicator);
    if (indicator.isCanceled()) {
      ApplicationManager.getApplication().invokeLater(
        () -> StudyCheckUtils.showTestResultPopUp("Check cancelled", MessageType.WARNING.getPopupBackground(), myProject));
    }
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    if (course != null) {
      final StudyTestsOutputParser.TestsOutput testsOutput = StudyTestsOutputParser.getTestsOutput(output, course.isAdaptive());
      String stderr = output.getStderr();
      if (!stderr.isEmpty() && output.getStdout().isEmpty()) {
        //log error output of tests
        LOG.info("#educational " + stderr);
        return new StudyTestsOutputParser.TestsOutput(false, stderr);
      }
      return testsOutput;
    }
    return null;
  }

  private void checkForAdaptiveCourse(@NotNull ProgressIndicator indicator) {
    final StepicUser user = StudySettings.getInstance().getUser();
    if (user == null) {
      LOG.warn("User is null");
      ApplicationManager.getApplication().invokeLater(() ->
                                                        StudyUtils.showErrorPopupOnToolbar(myProject,
                                                                                           "Failed to launch checking: you're not authorized"));
      return;
    }

    if (myTask instanceof ChoiceTask) {
      final Pair<Boolean, String> result = EduAdaptiveStepicConnector.checkChoiceTask(myProject, (ChoiceTask)myTask, user);
      processStepicCheckOutput(result);
    }
    else if (myTask instanceof TheoryTask) {
     myTask.setStatus(StudyStatus.Solved);
    }
    else {
      final StudyTestsOutputParser.TestsOutput testOutput = getTestOutput(indicator);
      if (testOutput != null) {
        // As tests in adaptive courses are created from
        // samples and stored in task, to disable it we should ignore local testing results
        if (StudySettings.getInstance().isEnableTestingFromSamples() && !testOutput.isSuccess()) {
          onTaskFailed(testOutput.getMessage());
        }
        else {
          final Pair<Boolean, String> pair = EduAdaptiveStepicConnector.checkCodeTask(myProject, myTask, user);
          processStepicCheckOutput(pair);
        }
      }
    }
  }

  private void processStepicCheckOutput(@Nullable Pair<Boolean, String> pair) {
    if (pair != null && pair.getFirst() != null) {
      if (pair.getFirst()) {
        onTaskSolved("Congratulations! Remote tests passed.");
      }
      else {
        final String checkMessage = pair.getSecond();
        onTaskFailed(checkMessage);
      }
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        String message = pair == null ? FAILED_CHECK_LAUNCH : pair.getSecond();
        StudyCheckUtils.showTestResultPopUp(message,
                                            MessageType.WARNING
                                              .getPopupBackground(),
                                            myProject);
      });
    }
  }

  protected void onTaskFailed(@NotNull String message) {
    myTask.setStatus(StudyStatus.Failed);
    myTask.getChecker(myProject).onTaskFailed(message);
  }

  protected void onTaskSolved(@NotNull String message) {
    myTask.setStatus(StudyStatus.Solved);
    myTask.getChecker(myProject).onTaskSolved(message);
  }

  private void runAfterTaskCheckedActions() {
    for (StudyCheckListener listener : StudyCheckListener.EP_NAME.getExtensions()) {
      listener.afterCheck(myProject, myTask);
    }
  }
}
