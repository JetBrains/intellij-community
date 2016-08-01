package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyActionListener;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;

public class CCStudyActionListener implements StudyActionListener {
  @Override
  public void beforeCheck(AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return;
    }
    if (!CCUtils.isCourseCreator(project)) {
      return;
    }
    VirtualFile virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(event.getDataContext());
    if (virtualFile == null) {
      return;
    }

    TaskFile taskFile = StudyUtils.getTaskFile(project, virtualFile);
    if (taskFile == null) {
      return;
    }

    Task task = taskFile.getTask();
    VirtualFile taskDir = StudyUtils.getTaskDir(virtualFile);
    if (taskDir == null) {
      return;
    }
    CCUtils.updateResources(project, task, taskDir);
  }
}
