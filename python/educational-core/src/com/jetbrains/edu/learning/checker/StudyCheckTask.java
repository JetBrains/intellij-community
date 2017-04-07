package com.jetbrains.edu.learning.checker;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.projectView.ProjectView;
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
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.ChoiceTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask;
import com.jetbrains.edu.learning.stepic.EduAdaptiveStepicConnector;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
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
      processStepicCheckOutput(indicator, result);
    }
    else if (myTask instanceof TheoryTask) {
      final int lessonId = myTask.getLesson().getId();
      final boolean reactionPosted = EduAdaptiveStepicConnector.postRecommendationReaction(String.valueOf(lessonId),
                                                                                           String.valueOf(user.getId()),
                                                                                           EduAdaptiveStepicConnector.NEXT_RECOMMENDATION_REACTION);

      if (reactionPosted) {
        if (myStatusBeforeCheck != StudyStatus.Solved) {
          myTask.setStatus(StudyStatus.Solved);
          EduAdaptiveStepicConnector.addNextRecommendedTask(myProject, myTask.getLesson(), indicator,
                                                            EduAdaptiveStepicConnector.NEXT_RECOMMENDATION_REACTION);
        }
      }
      else {
        ApplicationManager.getApplication().invokeLater(() ->
                                                          StudyUtils
                                                            .showErrorPopupOnToolbar(myProject, "Unable to get next recommendation"));
      }
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
          processStepicCheckOutput(indicator, pair);
        }
      }
    }
  }

  private void processStepicCheckOutput(@NotNull ProgressIndicator indicator, @Nullable Pair<Boolean, String> pair) {
    if (pair != null && pair.getFirst() != null) {
      if (pair.getFirst()) {
        onTaskSolved("Congratulations! Remote tests passed.");
        if (myStatusBeforeCheck != StudyStatus.Solved) {
          EduAdaptiveStepicConnector.addNextRecommendedTask(myProject, myTask.getLesson(), indicator,
                                                            EduAdaptiveStepicConnector.NEXT_RECOMMENDATION_REACTION);
        }
      }
      else {
        final String checkMessage = pair.getSecond();
        onTaskFailed(checkMessage);
      }
      runAfterTaskCheckedActions();
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

  protected void onTaskSolved(@NotNull String message) {
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    myTask.setStatus(StudyStatus.Solved);
    if (course != null) {
      if (course.isAdaptive()) {
        ApplicationManager.getApplication().invokeLater(
          () -> {
            if (myTask instanceof ChoiceTask) {
              StudyCheckUtils.showTestResultPopUp("Congratulations!", MessageType.INFO.getPopupBackground(), myProject);
            }
            else {
              StudyCheckUtils.showTestResultPopUp("Congratulations!", MessageType.INFO.getPopupBackground(), myProject);
              StudyCheckUtils.showTestResultsToolWindow(myProject, message, true);
            }
          });
      }
      else {
        if (myTask instanceof TaskWithSubtasks) {
          boolean hasMoreSubtasks = ((TaskWithSubtasks)myTask).activeSubtaskNotLast();
          final int activeSubtaskIndex = ((TaskWithSubtasks)myTask).getActiveSubtaskIndex();
          int visibleSubtaskIndex = activeSubtaskIndex + 1;

          ApplicationManager.getApplication().invokeLater(() -> {
            int subtaskSize = ((TaskWithSubtasks)myTask).getLastSubtaskIndex() + 1;
            String resultMessage = !hasMoreSubtasks ? message : "Subtask " + visibleSubtaskIndex + "/" + subtaskSize + " solved";
            StudyCheckUtils.showTestResultPopUp(resultMessage, MessageType.INFO.getPopupBackground(), myProject);
            if (hasMoreSubtasks) {
              int nextSubtaskIndex = activeSubtaskIndex + 1;
              StudySubtaskUtils.switchStep(myProject, (TaskWithSubtasks)myTask, nextSubtaskIndex);
              rememberAnswers(nextSubtaskIndex);
            }
          });
        }
        else {
          ApplicationManager.getApplication().invokeLater(
            () -> StudyCheckUtils.showTestResultPopUp(message, MessageType.INFO.getPopupBackground(), myProject));
        }
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
    for (StudyCheckListener listener : StudyCheckListener.EP_NAME.getExtensions()) {
      listener.afterCheck(myProject, myTask);
    }
  }
}
