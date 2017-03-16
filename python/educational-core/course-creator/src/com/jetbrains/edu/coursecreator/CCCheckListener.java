package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyCheckListener;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.NotNull;

public class CCCheckListener implements StudyCheckListener {

  @Override
  public void beforeCheck(@NotNull Project project, @NotNull Task task) {
    if (!CCUtils.isCourseCreator(project)) {
      return;
    }
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    CCUtils.updateResources(project, task, taskDir);
  }
}
