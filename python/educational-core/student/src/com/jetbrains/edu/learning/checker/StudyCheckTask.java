package com.jetbrains.edu.learning.checker;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.EduUtils;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.stepic.EduStepicConnector;
import com.jetbrains.edu.stepic.StudySettings;
import org.jetbrains.annotations.NotNull;

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
    final CapturingProcessHandler handler = new CapturingProcessHandler(myTestProcess, null, myCommandLine);
    final ProcessOutput output = handler.runProcessWithProgressIndicator(indicator);
    if (indicator.isCanceled()) {
      ApplicationManager.getApplication().invokeLater(
        () -> StudyCheckUtils.showTestResultPopUp("Check cancelled", MessageType.WARNING.getPopupBackground(), myProject));
      return;
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
      return;
    }

    postAttemptToStepic(testsOutput);


    if (testsOutput.isSuccess()) {
      onTaskSolved(testsOutput);
    }
    else {
      onTaskFailed(testsOutput);
    }
  }

  protected void onTaskFailed(StudyTestsOutputParser.TestsOutput testsOutput) {
    myTaskManger.setStatus(myTask, StudyStatus.Failed);
    ApplicationManager.getApplication().invokeLater(
      () -> StudyCheckUtils.showTestResultPopUp(testsOutput.getMessage(), MessageType.ERROR.getPopupBackground(), myProject));
  }

  protected void onTaskSolved(StudyTestsOutputParser.TestsOutput testsOutput) {
    myTaskManger.setStatus(myTask, StudyStatus.Solved);
    ApplicationManager.getApplication().invokeLater(
      () -> StudyCheckUtils.showTestResultPopUp(testsOutput.getMessage(), MessageType.INFO.getPopupBackground(), myProject));
  }

  protected void postAttemptToStepic(StudyTestsOutputParser.TestsOutput testsOutput) {
    final StudySettings studySettings = StudySettings.getInstance();
    final String login = studySettings.getLogin();
    final String password = StringUtil.isEmptyOrSpaces(login) ? "" : studySettings.getPassword();
    EduStepicConnector.postAttempt(myTask, testsOutput.isSuccess(), login, password);
  }
}
