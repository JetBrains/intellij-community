package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.coursecreator.CCProjectService;

public class CCAddAsTaskFile extends AnAction {
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
    Task task = CCProjectService.getInstance(project).getTask(file);
    if (task == null) {
      return;
    }
    task.addTaskFile(file.getName(), task.getTaskFiles().size());
    ProjectView.getInstance(project).refresh();
  }


  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (file == null || file.isDirectory() || CCProjectService.getInstance(project).getTaskFile(file) != null) {
      presentation.setEnabledAndVisible(false);
    }
  }
}
