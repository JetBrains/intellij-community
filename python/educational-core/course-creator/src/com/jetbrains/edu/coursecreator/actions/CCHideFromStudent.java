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
    final VirtualFile taskDir = StudyUtils.getTaskDir(file);
    if (taskDir == null) {
      return;
    }

    final String relativePath = StudyUtils.getRelativePath(taskDir, file);
    VirtualFile patternFile = StudyUtils.getPatternFile(taskFile, relativePath);
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
    taskFiles.remove(relativePath);
  }

  @Override
  protected boolean isAvailable(Project project, VirtualFile file) {
    return StudyUtils.getTaskFile(project, file) != null;
  }
}
