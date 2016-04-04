package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

public class CCVirtualFileListener extends VirtualFileAdapter {

  @Override
  public void fileCreated(@NotNull VirtualFileEvent event) {
    VirtualFile createdFile = event.getFile();
    Project project = ProjectUtil.guessProjectForContentFile(createdFile);
    if (project == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null || !CCUtils.isCourseCreator(project)) {
      return;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(project, createdFile);
    if (taskFile != null) {
      return;
    }

    if (CCUtils.isTestsFile(project, createdFile) || EduNames.TASK_HTML.equals(createdFile.getName())) {
      return;
    }

    VirtualFile taskVF = createdFile.getParent();
    if (taskVF == null) {
      return;
    }
    Task task = StudyUtils.getTask(project,taskVF);
    if (task == null) {
      return;
    }
    task.addTaskFile(createdFile.getName(), 1);
  }
}
