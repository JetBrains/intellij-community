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
 * move caret to next task window
 */
public class StudyNextWindowAction extends DumbAwareAction {
  public static final String ACTION_ID = "NextWindow";
  public static final String SHORTCUT = "ctrl pressed PERIOD";
  public static final String SHORTCUT2 = "ctrl pressed ENTER";

  public StudyNextWindowAction() {
    super("NextWindowAction", "Select next window", StudyIcons.Next);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      Editor selectedEditor = StudyEditor.getSelectedEditor(project);
      if (selectedEditor != null) {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
        if (openedFile != null) {
          StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
          TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
          if (selectedTaskFile != null) {
            TaskWindow selectedTaskWindow = selectedTaskFile.getSelectedTaskWindow();
            boolean ifDraw = false;
            for (TaskWindow taskWindow : selectedTaskFile.getTaskWindows()) {
              if (ifDraw) {
                selectedTaskFile.setSelectedTaskWindow(taskWindow);
                taskWindow.draw(selectedEditor, taskWindow.getStatus() != StudyStatus.Solved, true);
                return;
              }
              if (taskWindow == selectedTaskWindow) {
                ifDraw = true;
              }
            }
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
