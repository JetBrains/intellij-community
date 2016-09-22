package com.jetbrains.edu.learning;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.edu.learning.checker.StudyCheckUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class StudySubtaskUtils {
  private StudySubtaskUtils() {
  }

  /***
   * @param toSubtaskIndex from 0 to subtaskNum - 1
   */
  public static void switchStep(@NotNull Project project, @NotNull Task task, int toSubtaskIndex) {
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
    task.setActiveSubtaskIndex(toSubtaskIndex);
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
}