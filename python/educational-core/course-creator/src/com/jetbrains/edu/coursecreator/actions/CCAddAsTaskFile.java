package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;

public class CCAddAsTaskFile extends CCTaskFileActionBase {

  protected void performAction(VirtualFile file, Task task, Course course, Project project) {
    task.addTaskFile(file.getName(), task.getTaskFiles().size());
    CCUtils.createResourceFile(file, course, StudyUtils.getTaskDir(file));
  }

  protected boolean isAvailable(Project project, VirtualFile file) {
    return StudyUtils.getTaskFile(project, file) == null && !CCUtils.isTestsFile(project, file);
  }
}
