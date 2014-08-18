package com.jetbrains.python.edu.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;
import com.jetbrains.python.edu.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract public class StudyWindowNavigationAction extends DumbAwareAction {

  public StudyWindowNavigationAction(String actionId, String description, Icon icon) {
    super(actionId, description, icon);
  }

  public void navigateWindow(@NotNull final Project project) {
      Editor selectedEditor = StudyEditor.getSelectedEditor(project);
      if (selectedEditor != null) {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
        if (openedFile != null) {
          StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
          TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
          if (selectedTaskFile != null) {
            TaskWindow selectedTaskWindow = selectedTaskFile.getSelectedTaskWindow();
            if (selectedTaskWindow == null) {
              return;
            }
            TaskWindow nextTaskWindow = getNextTaskWindow(selectedTaskWindow);
            if (nextTaskWindow == null) {
              return;
            }
            nextTaskWindow.draw(selectedEditor, true, true);
            selectedTaskFile.setSelectedTaskWindow(nextTaskWindow);
            }
          }
        }
      }

  @Nullable
  protected abstract TaskWindow getNextTaskWindow(@NotNull final TaskWindow window);

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    navigateWindow(project);
  }

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
