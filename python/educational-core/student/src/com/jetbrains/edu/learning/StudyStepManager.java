package com.jetbrains.edu.learning;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

public class StudyStepManager {

  private static final Logger LOG = Logger.getInstance(StudyStepManager.class);

  public static void switchStep(@NotNull Project project, @NotNull Task task, int step) {
    if (task.getActiveStepIndex() == step) {
      return;
    }
    task.setActiveStepIndex(step);

    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    VirtualFile srcDir = taskDir.findChild(EduNames.SRC);
    if (srcDir != null) {
      taskDir = srcDir;
    }
    for (Map.Entry<String, TaskFile> entry : task.getTaskFiles().entrySet()) {
      String name = entry.getKey();
      VirtualFile virtualFile = taskDir.findChild(name);
      if (virtualFile == null) {
        continue;
      }
      EditorNotifications.getInstance(project).updateNotifications(virtualFile);
    }
    update(project, task, taskDir);

  }

  private static void update(@NotNull Project project, @NotNull Task task, VirtualFile taskDir) {
    StudyCheckUtils.drawAllPlaceholders(project, task, taskDir);
    ProjectView.getInstance(project).refresh();
    StudyToolWindow toolWindow = StudyUtils.getStudyToolWindow(project);
    if (toolWindow != null) {
      String text = StudyUtils.getTaskTextFromTask(taskDir, task);
      if (text == null) {
        toolWindow.setEmptyText(project);
        return;
      }
      toolWindow.setTaskText(text, taskDir, project);
    }
  }

  public static void deleteStep(@NotNull Project project, @NotNull Task task, int index) {
    //TODO: delete not only the last step
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }

    ArrayList<VirtualFile> filesToDelete = new ArrayList<>();
    for (VirtualFile file : taskDir.getChildren()) {
      String stepSuffix = EduNames.STEP_MARKER + index;
      if (file.getName().contains(stepSuffix)) {
        filesToDelete.add(file);
      }
    }
    for (VirtualFile file : filesToDelete) {
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            file.delete(StudyStepManager.class);
          }
          catch (IOException e) {
            LOG.error(e);
          }
        });
    }

    task.getAdditionalSteps().remove(index);
    if (task.getActiveStepIndex() == index) {
      switchStep(project, task, index - 1);
    }
  }
}
