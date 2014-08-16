package com.jetbrains.python.edu;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.editor.event.EditorMouseAdapter;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.edu.course.StudyStatus;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.course.TaskWindow;
import com.jetbrains.python.edu.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


class StudyEditorFactoryListener implements EditorFactoryListener {

  /**
   * draws selected task window if there is one located in mouse position
   */
  private static class WindowSelectionListener extends EditorMouseAdapter {
    private final TaskFile myTaskFile;

    WindowSelectionListener(TaskFile taskFile) {
      myTaskFile = taskFile;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      Editor editor = e.getEditor();
      Point point = e.getMouseEvent().getPoint();
      LogicalPosition pos = editor.xyToLogicalPosition(point);
      TaskWindow taskWindow = myTaskFile.getTaskWindow(editor.getDocument(), pos);
      if (taskWindow != null) {
        myTaskFile.setSelectedTaskWindow(taskWindow);
        taskWindow.draw(editor, taskWindow.getStatus() != StudyStatus.Solved, true);
      }
      else {
        myTaskFile.drawAllWindows(editor);
      }
    }
  }

  @Override
  public void editorCreated(@NotNull final EditorFactoryEvent event) {
    final Editor editor = event.getEditor();

    final Project project = editor.getProject();
    if (project == null) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              Document document = editor.getDocument();
              VirtualFile openedFile = FileDocumentManager.getInstance().getFile(document);
              if (openedFile != null) {
                StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
                TaskFile taskFile = taskManager.getTaskFile(openedFile);
                if (taskFile != null) {
                  taskFile.navigateToFirstTaskWindow(editor);
                  editor.addEditorMouseListener(new WindowSelectionListener(taskFile));
                  StudyDocumentListener listener = new StudyDocumentListener(taskFile);
                  StudyEditor.addDocumentListener(document, listener);
                  document.addDocumentListener(listener);
                  taskFile.drawAllWindows(editor);
                }
              }
            }
          });
        }
      }
    );
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    Document document = editor.getDocument();
    StudyDocumentListener listener = StudyEditor.getListener(document);
    if (listener != null) {
      document.removeDocumentListener(listener);
      StudyEditor.removeListener(document);
    }
    editor.getMarkupModel().removeAllHighlighters();
    editor.getSelectionModel().removeSelection();
  }
}
