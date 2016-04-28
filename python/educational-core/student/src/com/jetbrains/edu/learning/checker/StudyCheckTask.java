package com.jetbrains.edu.learning.checker;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyPluginConfigurator;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
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
  private Ref<Boolean> myCheckInProcess;
  private final Process myTestProcess;
  private final String myCommandLine;

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
    myStatusBeforeCheck = myTaskManger.getStatus(myTask);
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
    myTaskManger.setStatus(myTask, myStatusBeforeCheck);
    clearState();
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    if (course != null && course.isAdaptive()) {
      checkForAdaptiveCourse(indicator);
    }
    else {
      checkForEduCourse(indicator);
    }
  }

  private void checkForEduCourse(@NotNull ProgressIndicator indicator) {
    final StudyTestsOutputParser.TestsOutput testsOutput = getTestOutput(indicator);

    postAttemptToStepic(testsOutput);

    if (testsOutput != null) {
      if (testsOutput.isSuccess()) {
        onTaskSolved(testsOutput.getMessage());
      }
      else {
        onTaskFailed(testsOutput.getMessage());
      }
      runAfterTaskCheckedActions();
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


    final StudyTestsOutputParser.TestsOutput testsOutput = StudyTestsOutputParser.getTestsOutput(output);
    String stderr = output.getStderr();
    if (!stderr.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(() ->
                                                        StudyCheckUtils.showTestResultPopUp("Failed to launch checking",
                                                                                            MessageType.WARNING.getPopupBackground(),
                                                                                            myProject));
      //log error output of tests
      LOG.info("#educational " + stderr);
      return null;
    }
    return testsOutput;
  }

  private void checkForAdaptiveCourse(ProgressIndicator indicator) {
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(new Backgroundable(myProject, "Checking Task") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final StudyTestsOutputParser.TestsOutput testOutput = getTestOutput(indicator);
        if (testOutput != null) {
          if (testOutput.isSuccess()) {
            final Pair<Boolean, String> pair = EduAdaptiveStepicConnector.checkTask(myProject, myTask);
            if (pair != null && !pair.getSecond().isEmpty()) {
              final String checkMessage = pair.getSecond();
              if (pair.getFirst()) {
                onTaskSolved(checkMessage);
              }
              else {
                onTaskFailed(checkMessage);
              }
              runAfterTaskCheckedActions();
            }
            else {
              ApplicationManager.getApplication().invokeLater(() ->
                                                                StudyCheckUtils.showTestResultPopUp("Failed to launch checking",
                                                                                                    MessageType.WARNING
                                                                                                      .getPopupBackground(),
                                                                                                    myProject));
            }
          }
          else {
            onTaskFailed(testOutput.getMessage());
          }
        }
      }
    }, indicator);
  }

  protected void onTaskFailed(String message) {
    myTaskManger.setStatus(myTask, StudyStatus.Failed);
    ApplicationManager.getApplication().invokeLater(
      () -> StudyCheckUtils.showTestResults(myProject, message));
  }

  protected void onTaskSolved(String message) {
    myTaskManger.setStatus(myTask, StudyStatus.Solved);

    ApplicationManager.getApplication().invokeLater(
      () -> StudyCheckUtils.showTestResults(myProject, message));
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

  protected void postAttemptToStepic(StudyTestsOutputParser.TestsOutput testsOutput) {
    final StudyTaskManager studySettings = StudyTaskManager.getInstance(myProject);
    final String login = studySettings.getLogin();
    final String password = StringUtil.isEmptyOrSpaces(login) ? "" : studySettings.getPassword();
    EduStepicConnector.postAttempt(myTask, testsOutput.isSuccess(), login, password);
  }
}
