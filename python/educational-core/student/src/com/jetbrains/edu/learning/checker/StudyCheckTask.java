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
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
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
    StudyCheckUtils.drawAllPlaceholders(myProject, myTask, myTaskDir);
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
      runAfterTaskCheckedActions();
      final Course course = StudyTaskManager.getInstance(myProject).getCourse();
      if (course != null && EduNames.STUDY.equals(course.getCourseMode())) {
        EduStepicConnector.postAttempt(myTask, testsOutput.isSuccess(), myProject);
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

  private void checkForAdaptiveCourse(ProgressIndicator indicator) {
    final StudyTestsOutputParser.TestsOutput testOutput = getTestOutput(indicator);
    if (testOutput != null) {
      // As tests in adaptive courses are created from
      // samples and stored in task, to disable it we should ignore local testing results
      if (StudyTaskManager.getInstance(myProject).isEnableTestingFromSamples() && !testOutput.isSuccess()) {
        onTaskFailed(testOutput.getMessage());
      }
      else {
        final Pair<Boolean, String> pair = EduAdaptiveStepicConnector.checkTask(myProject, myTask);
        if (pair != null && !(!pair.getFirst() && pair.getSecond().isEmpty())) {
          if (pair.getFirst()) {
            onTaskSolved("Congratulations! Remote tests passed.");
            if (myStatusBeforeCheck != StudyStatus.Solved) {
              EduAdaptiveStepicConnector.addNextRecommendedTask(myProject, 2, indicator);
            }
          }
          else {
            final String checkMessage = pair.getSecond();
            onTaskFailed(checkMessage);
          }
          runAfterTaskCheckedActions();
        }
        else {
          ApplicationManager.getApplication().invokeLater(() -> StudyCheckUtils.showTestResultPopUp(FAILED_CHECK_LAUNCH,
                                                                                                    MessageType.WARNING
                                                                                                      .getPopupBackground(),
                                                                                                    myProject));
        }
      }
    }
  }

  protected void onTaskFailed(String message) {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    myTask.setStatus(StudyStatus.Failed);
    if (course != null) {
      if (course.isAdaptive()) {
        ApplicationManager.getApplication().invokeLater(
          () -> {
            StudyCheckUtils.showTestResultPopUp("Failed", MessageType.ERROR.getPopupBackground(), myProject);
            StudyCheckUtils.showTestResultsToolWindow(myProject, message, false);
          });
      }
      else {
        ApplicationManager.getApplication()
          .invokeLater(() -> StudyCheckUtils.showTestResultPopUp(message, MessageType.ERROR.getPopupBackground(), myProject));
      }
    }
  }

  protected void onTaskSolved(String message) {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    myTask.setStatus(StudyStatus.Solved);
    if (course != null) {
      if (course.isAdaptive()) {
        ApplicationManager.getApplication().invokeLater(
          () -> {
            StudyCheckUtils.showTestResultPopUp("Congratulations!", MessageType.INFO.getPopupBackground(), myProject);
            StudyCheckUtils.showTestResultsToolWindow(myProject, message, true);
          });
      }
      else {
        ApplicationManager.getApplication()
          .invokeLater(() -> StudyCheckUtils.showTestResultPopUp(message, MessageType.INFO.getPopupBackground(), myProject));
      }
    }
  }

  private void runAfterTaskCheckedActions() {
    StudyPluginConfigurator configurator = StudyUtils.getConfigurator(myProject);
    if (configurator != null) {
      StudyAfterCheckAction[] checkActions = configurator.getAfterCheckActions();
      if (checkActions != null) {
        for (StudyAfterCheckAction action : checkActions) {
          action.run(myProject, myTask, myStatusBeforeCheck);
        }
      }
    }
    else {
      LOG.warn("No configurator is provided for the plugin");
    }
  }
}
