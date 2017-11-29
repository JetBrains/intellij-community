package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
    final VirtualFile[] virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (virtualFiles == null) {
      return;
    }

    for (VirtualFile file : virtualFiles) {
      if (!isAvailable(project, file)) continue;
      VirtualFile taskVF = StudyUtils.getTaskDir(file);
      if (taskVF == null) {
        return;
      }
      Task task = StudyUtils.getTask(project, taskVF);
      if (task == null) {
        return;
      }
      Course course = StudyTaskManager.getInstance(project).getCourse();
      if (file.isDirectory()) {
        final List<VirtualFile> children = VfsUtil.collectChildrenRecursively(file);
        for (VirtualFile child : children) {
          performAction(child, task, course, project);
        }
      }
      else {
        performAction(file, task, course, project);
      }
    }
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
    final VirtualFile[] virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (virtualFiles == null || virtualFiles.length == 0) {
      presentation.setEnabledAndVisible(false);
    }
  }

  protected abstract boolean isAvailable(Project project, VirtualFile file);
}
