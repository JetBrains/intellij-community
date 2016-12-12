package com.jetbrains.edu.learning.checker;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.*;
import com.jetbrains.edu.learning.actions.StudyAfterCheckAction;
import com.jetbrains.edu.learning.actions.StudyRunAction;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.*;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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
  private boolean myRunTestFile = true;
  private static final String FAILED_CHECK_LAUNCH = "Failed to launch checking";
  private static final String DO_NOT_RUN_ON_CHECK = "DO_NOT_RUN_ON_CHECK";

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
    }
  }

  private void checkForEduCourse(@NotNull ProgressIndicator indicator) {
    final StudyTestsOutputParser.TestsOutput testsOutput = getTestOutput(indicator);
    if (myRunTestFile) {
      ApplicationManager.getApplication().invokeLater(() -> runTask(myProject));
    }
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

  private static void runTask(@NotNull Project project) {
    final StudyRunAction runAction = (StudyRunAction)ActionManager.getInstance().getAction(StudyRunAction.ACTION_ID);
    if (runAction == null) {
      return;
    }
    runAction.run(project);
  }

  @Nullable
  private StudyTestsOutputParser.TestsOutput getTestOutput(@NotNull ProgressIndicator indicator) {
    final CapturingProcessHandler handler = new CapturingProcessHandler(myTestProcess, null, myCommandLine);
    final ProcessOutput output = handler.runProcessWithProgressIndicator(indicator);
    if (indicator.isCanceled()) {
      ApplicationManager.getApplication().invokeLater(
        () -> StudyCheckUtils.showTestResultPopUp("Check cancelled", MessageType.WARNING.getPopupBackground(), myProject));
    }
    myRunTestFile = !output.getStdout().contains(DO_NOT_RUN_ON_CHECK);
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
        boolean hasMoreSubtasks = myTask.hasSubtasks() && myTask.getActiveSubtaskIndex() != myTask.getLastSubtaskIndex();
        int visibleSubtaskIndex = myTask.getActiveSubtaskIndex() + 1;
        ApplicationManager.getApplication().invokeLater(() -> {
          int subtaskSize = myTask.getLastSubtaskIndex() + 1;
          String resultMessage = !hasMoreSubtasks ? message : "Subtask " + visibleSubtaskIndex + "/" + subtaskSize + " solved";
          StudyCheckUtils.showTestResultPopUp(resultMessage, MessageType.INFO.getPopupBackground(), myProject);
          if (hasMoreSubtasks) {
            int nextSubtaskIndex = myTask.getActiveSubtaskIndex() + 1;
            StudySubtaskUtils.switchStep(myProject, myTask, nextSubtaskIndex);
            rememberAnswers(nextSubtaskIndex);
          }
        });
      }
    }
  }

  private void rememberAnswers(int nextSubtaskIndex) {
    VirtualFile taskDir = myTask.getTaskDir(myProject);
    if (taskDir == null) {
      return;
    }
    VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
    if (srcDir != null) {
      taskDir = srcDir;
    }
    for (Map.Entry<String, TaskFile> entry : myTask.getTaskFiles().entrySet()) {
      TaskFile taskFile = entry.getValue();
      VirtualFile virtualFile = taskDir.findFileByRelativePath(entry.getKey());
      if (virtualFile == null) {
        continue;
      }
      Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      if (document == null) {
        continue;
      }
      for (AnswerPlaceholder placeholder : taskFile.getActivePlaceholders()) {
        if (placeholder.getSubtaskInfos().containsKey(nextSubtaskIndex - 1)) {
          int offset = placeholder.getOffset();
          String answer = document.getText(TextRange.create(offset, offset + placeholder.getRealLength()));
          placeholder.getSubtaskInfos().get(nextSubtaskIndex - 1).setAnswer(answer);
        }
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
