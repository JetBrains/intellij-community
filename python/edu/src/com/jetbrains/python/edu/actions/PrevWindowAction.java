package com.jetbrains.python.edu.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.StudyStatus;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;
import com.jetbrains.python.edu.editor.StudyEditor;
import icons.StudyIcons;

/**
 * author: liana
 * data: 6/30/14.
 */
public class PrevWindowAction extends DumbAwareAction {
  public static final String ACTION_ID = "PrevWindowAction";
  public static final String SHORTCUT = "ctrl pressed COMMA";

  public PrevWindowAction() {
    super("PrevWindowAction", "Select previous window", StudyIcons.Prev);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;
    Editor selectedEditor = StudyEditor.getSelectedEditor(project);
    if (selectedEditor != null) {
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      if (openedFile != null) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
        TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
        if (selectedTaskFile != null) {
          TaskWindow selectedTaskWindow = selectedTaskFile.getSelectedTaskWindow();
          TaskWindow prev = null;
          for (TaskWindow taskWindow : selectedTaskFile.getTaskWindows()) {
            if (taskWindow == selectedTaskWindow) {
              break;
            }
            prev = taskWindow;
          }

          if (prev != null) {
            selectedTaskFile.setSelectedTaskWindow(prev);
            prev.draw(selectedEditor, prev.getStatus() != StudyStatus.Solved, true);
          }
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
