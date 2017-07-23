package com.jetbrains.edu.learning.checker;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.actions.StudyCheckAction;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.StudyStatus;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.PyCharmTask;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.stepic.EduStepicConnector;
import com.jetbrains.edu.learning.stepic.StepicUser;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class PyStudyTaskChecker extends StudyTaskChecker<PyCharmTask> {
  private static final Logger LOG = Logger.getInstance(PyStudyTaskChecker.class);

  public PyStudyTaskChecker(PyCharmTask task, Project project) {
    super(task, project);
  }

  @Override
  public StudyCheckResult check() {
    VirtualFile taskDir = myTask.getTaskDir(myProject);
    if (taskDir == null) {
      LOG.info("taskDir is null for task " + myTask.getName());
      return new StudyCheckResult(StudyStatus.Unchecked, "Task is broken");
    }
    CountDownLatch latch = new CountDownLatch(1);
    ApplicationManager.getApplication()
      .invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> {
        StudyCheckUtils.flushWindows(myTask, taskDir);
        latch.countDown();
      }));
    final PyStudyTestRunner testRunner = new PyStudyTestRunner(myTask, taskDir);
    try {
      final VirtualFile fileToCheck = getTaskVirtualFile(myTask, taskDir);
      if (fileToCheck != null) {
        //otherwise answer placeholders might have been not flushed yet
        latch.await();
        Process testProcess = testRunner.createCheckProcess(myProject, fileToCheck.getPath());
        StudyTestsOutputParser.TestsOutput output =
          StudyCheckUtils
            .getTestOutput(testProcess, testRunner.getCommandLine().getCommandLineString(), myTask.getLesson().getCourse().isAdaptive());
        return new StudyCheckResult(output.isSuccess() ? StudyStatus.Solved : StudyStatus.Failed, output.getMessage());
      }
    }
    catch (ExecutionException | InterruptedException e) {
      LOG.error(e);
    }
    return new StudyCheckResult(StudyStatus.Unchecked, StudyCheckAction.FAILED_CHECK_LAUNCH);
  }

  @Override
  public void clearState() {
    ApplicationManager.getApplication().invokeLater(() -> {
      StudyCheckUtils.drawAllPlaceholders(myProject, myTask);
      VirtualFile taskDir = myTask.getTaskDir(myProject);
      if (taskDir != null) {
        EduUtils.deleteWindowDescriptions(myTask, taskDir);
      }
    });
  }

  @Override
  public void onTaskFailed(@NotNull String message) {
    super.onTaskFailed(message);
    ApplicationManager.getApplication().invokeLater(() -> {
      VirtualFile taskDir = myTask.getTaskDir(myProject);
      if (taskDir == null) return;
      for (Map.Entry<String, TaskFile> entry : myTask.getTaskFiles().entrySet()) {
        final String name = entry.getKey();
        final TaskFile taskFile = entry.getValue();
        if (taskFile.getActivePlaceholders().size() < 2) {
          continue;
        }
        final Course course = myTask.getLesson().getCourse();
        if (course != null && EduNames.STUDY.equals(course.getCourseMode())) {
          CommandProcessor.getInstance().runUndoTransparentAction(
            () -> ApplicationManager.getApplication().runWriteAction(
              () -> PyStudySmartChecker.runSmartTestProcess(taskDir, new PyStudyTestRunner(myTask, taskDir), name, taskFile, myProject)));
        }
      }
      StudyCheckUtils.navigateToFailedPlaceholder(new StudyState(StudyUtils.getSelectedStudyEditor(myProject)), myTask, taskDir, myProject);
    });
  }

  @Nullable
  private static VirtualFile getTaskVirtualFile(@NotNull final Task task,
                                                @NotNull final VirtualFile taskDir) {

    Map<TaskFile, VirtualFile> fileMap =
      EntryStream.of(task.getTaskFiles()).invert().mapValues(name -> taskDir.findFileByRelativePath(name)).nonNullValues().toMap();
    Map.Entry<TaskFile, VirtualFile> entry = EntryStream.of(fileMap).findAny(e -> !e.getKey().getActivePlaceholders().isEmpty())
      .orElse(fileMap.entrySet().stream().findFirst().orElse(null));
    return entry == null ? null : entry.getValue();
  }

  @Override
  public StudyCheckResult checkOnRemote(@Nullable StepicUser user) {
    StudyCheckResult result = check();
    final Course course = StudyTaskManager.getInstance(myProject).getCourse();
    StudyStatus status = result.getStatus();
    if (user != null && course != null && EduNames.STUDY.equals(course.getCourseMode()) && status != StudyStatus.Unchecked) {
      EduStepicConnector.postSolution(myTask, status == StudyStatus.Solved, myProject);
    }
    return result;
  }
}
