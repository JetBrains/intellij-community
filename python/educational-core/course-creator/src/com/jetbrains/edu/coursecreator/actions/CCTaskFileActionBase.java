package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
import org.jetbrains.annotations.Nullable;

public abstract class CCTaskFileActionBase extends AnAction {
  public CCTaskFileActionBase(@Nullable String text) {
    super(text);
  }

  @Override
  public void actionPerformed(final AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (file == null) {
      return;
    }
    VirtualFile taskVF = file.getParent();
    if (taskVF == null) {
      return;
    }
    Task task = StudyUtils.getTask(project, taskVF);
    if (task == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    performAction(file, task, course, project);
  }

  protected abstract void performAction(VirtualFile file, Task task, Course course, Project project);


  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null || !CCUtils.isCourseCreator(project)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (file == null || file.isDirectory() || !isAvailable(project, file)) {
      presentation.setEnabledAndVisible(false);
    }
  }

  protected abstract boolean isAvailable(Project project, VirtualFile file);
}
