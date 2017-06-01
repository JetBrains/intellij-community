package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;

import java.io.IOException;

public class CCAddAsTestFile extends CCTaskFileActionBase {
  private static final Logger LOG = Logger.getInstance(CCAddAsTestFile.class.getName());
  public static final String ACTION_NAME = "Add file as test";

  public CCAddAsTestFile() {
    super(ACTION_NAME);
  }


  protected void performAction(VirtualFile file, Task task, Course course, Project project) {
    final String testRelativePath = FileUtil.getRelativePath(task.getTaskDir(project).getPath(), file.getPath(), '/');
    try {
      task.addTestsTexts(testRelativePath, VfsUtilCore.loadText(file));
    }
    catch (IOException e) {
      LOG.warn("Failed to load test file text");
    }
    ProjectView.getInstance(project).refresh();

  }

  protected boolean isAvailable(Project project, VirtualFile file) {
    return true;
  }
}
