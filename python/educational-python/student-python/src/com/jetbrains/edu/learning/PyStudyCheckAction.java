package com.jetbrains.edu.learning;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.jetbrains.edu.courseFormat.StudyStatus;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.actions.StudyCheckAction;
import com.jetbrains.edu.learning.actions.StudyRunAction;
import com.jetbrains.edu.learning.checker.StudyCheckTask;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.checker.StudyTestsOutputParser;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.checker.StudyTestRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class PyStudyCheckAction extends StudyCheckAction {
  private static final Logger LOG = Logger.getInstance(PyStudyCheckAction.class);

  @Override
  protected void check(@NotNull Project project) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      CommandProcessor.getInstance().runUndoTransparentAction(() -> {
        final StudyEditor selectedEditor = StudyUtils.getSelectedStudyEditor(project);
        if (selectedEditor == null) return;
        final StudyState studyState = new StudyState(selectedEditor);
        if (!studyState.isValid()) {
          LOG.info("StudyCheckAction was invoked outside study editor");
          return;
        }
        if (StudyCheckUtils.hasBackgroundProcesses(project)) return;


        if (!runTask(project)) return;

        final Task task = studyState.getTask();
        final VirtualFile taskDir = studyState.getTaskDir();
        StudyCheckUtils.flushWindows(task, taskDir);


        ApplicationManager.getApplication().invokeLater(
          () -> IdeFocusManager.getInstance(project).requestFocus(studyState.getEditor().getComponent(), true));

        final StudyTestRunner testRunner = StudyUtils.getTestRunner(task, taskDir);
        Process testProcess = null;
        String commandLine = "";
        try {
          final VirtualFile executablePath = getTaskVirtualFile(studyState, task, taskDir);
          if (executablePath != null) {
            commandLine = executablePath.getPath();
            testProcess = testRunner.createCheckProcess(project, commandLine);
          }
        }
        catch (ExecutionException e) {
          LOG.error(e);
        }
        if (testProcess == null) {
          return;
        }
        myCheckInProgress.set(true);
        StudyCheckTask checkTask = getCheckTask(project, studyState, testRunner, testProcess, commandLine);
        ProgressManager.getInstance().run(checkTask);
      });
    });
  }

  private static boolean runTask(@NotNull Project project) {
    final StudyRunAction runAction = (StudyRunAction)ActionManager.getInstance().getAction(StudyRunAction.ACTION_ID);
    if (runAction == null) {
      return false;
    }
    runAction.run(project);
    return true;
  }

  @NotNull
  private StudyCheckTask getCheckTask(@NotNull final Project project,
                                      final StudyState studyState,
                                      final StudyTestRunner testRunner,
                                      final Process testProcess,
                                      final String commandLine) {
    return new StudyCheckTask(project, studyState, myCheckInProgress, testProcess, commandLine) {
            @Override
            protected void onTaskFailed(StudyTestsOutputParser.TestsOutput testsOutput) {
              ApplicationManager.getApplication().invokeLater(() -> {
                if (myTaskDir == null) return;
                myTaskManger.setStatus(myTask, StudyStatus.Failed);
                for (Map.Entry<String, TaskFile> entry : myTask.getTaskFiles().entrySet()) {
                  final String name = entry.getKey();
                  final TaskFile taskFile = entry.getValue();
                  if (taskFile.getAnswerPlaceholders().size() < 2) {
                    myTaskManger.setStatus(taskFile, StudyStatus.Failed);
                    continue;
                  }
                  CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
                    StudyCheckUtils.runSmartTestProcess(myTaskDir, testRunner, name, taskFile, project);
                  }));
                }
                StudyCheckUtils.showTestResultPopUp(testsOutput.getMessage(), MessageType.ERROR.getPopupBackground(), project);
                StudyCheckUtils.navigateToFailedPlaceholder(myStudyState, myTask, myTaskDir, project);
              });
            }
          };
  }


  @Nullable
  private static VirtualFile getTaskVirtualFile(@NotNull final StudyState studyState,
                                         @NotNull final Task task,
                                         @NotNull final VirtualFile taskDir) {
    VirtualFile taskVirtualFile = studyState.getVirtualFile();
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      TaskFile taskFile = entry.getValue();
      VirtualFile virtualFile = taskDir.findChild(name);
      if (virtualFile != null) {
        if (!taskFile.getAnswerPlaceholders().isEmpty()) {
          taskVirtualFile = virtualFile;
        }
      }
    }
    return taskVirtualFile;
  }
}
