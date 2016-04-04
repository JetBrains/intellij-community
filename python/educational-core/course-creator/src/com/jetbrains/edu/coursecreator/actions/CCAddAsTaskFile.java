package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.coursecreator.CCProjectService;

import java.io.IOException;

public class CCAddAsTaskFile extends AnAction {
  private static final Logger LOG = Logger.getInstance(CCAddAsTaskFile.class);

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
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        String name = file.getNameWithoutExtension();
        String extension = file.getExtension();
        try {
          file.rename(this, name + ".answer." + extension);
        }
        catch (IOException e1) {
          LOG.error(e1);
        }
      }
    });
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
    if (file == null || file.isDirectory() || CCProjectService.getInstance(project).isAnswerFile(file)) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    Task task = CCProjectService.getInstance(project).getTask(file);
    if (task == null) {
      presentation.setEnabledAndVisible(false);
    }
  }
}
