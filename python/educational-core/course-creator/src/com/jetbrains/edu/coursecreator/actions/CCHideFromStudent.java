package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;

import java.io.IOException;
import java.util.Map;

public class CCHideFromStudent extends CCTaskFileActionBase {

  private static final Logger LOG = Logger.getInstance(CCHideFromStudent.class);

  @Override
  protected void performAction(VirtualFile file, Task task, Course course, Project project) {
    Map<String, TaskFile> taskFiles = task.getTaskFiles();
    TaskFile taskFile = StudyUtils.getTaskFile(project, file);
    if (taskFile == null) {
      return;
    }
    String name = file.getName();
    VirtualFile patternFile = StudyUtils.getPatternFile(taskFile, name);
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (patternFile != null) {
        try {
          patternFile.delete(CCHideFromStudent.class);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    });
    taskFiles.remove(name);
  }

  @Override
  protected boolean isAvailable(Project project, VirtualFile file) {
    return StudyUtils.getTaskFile(project, file) != null;
  }
}
