package com.jetbrains.edu.learning;


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
import com.intellij.problems.WolfTheProblemSolver;
import com.jetbrains.edu.learning.course.TaskFile;
import com.jetbrains.edu.learning.course.AnswerPlaceholder;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


class StudyEditorFactoryListener implements EditorFactoryListener {

  /**
   * draws selected task window if there is one located in mouse position
   */
  private static class WindowSelectionListener extends EditorMouseAdapter {
    private final TaskFile myTaskFile;

    WindowSelectionListener(@NotNull final TaskFile taskFile) {
      myTaskFile = taskFile;
    }

    @Override
    public void mouseClicked(EditorMouseEvent e) {
      final Editor editor = e.getEditor();
      final Point point = e.getMouseEvent().getPoint();
      final LogicalPosition pos = editor.xyToLogicalPosition(point);
      final AnswerPlaceholder answerPlaceholder = myTaskFile.getTaskWindow(editor.getDocument(), pos);
      if (answerPlaceholder != null) {
        myTaskFile.setSelectedAnswerPlaceholder(answerPlaceholder);
        answerPlaceholder.draw(editor);
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
              final Document document = editor.getDocument();
              final VirtualFile openedFile = FileDocumentManager.getInstance().getFile(document);
              if (openedFile != null) {
                final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
                final TaskFile taskFile = taskManager.getTaskFile(openedFile);
                if (taskFile != null) {
                  taskFile.navigateToFirstTaskWindow(editor);
                  editor.addEditorMouseListener(new WindowSelectionListener(taskFile));
                  StudyEditor.addDocumentListener(document, new StudyDocumentListener(taskFile));
                  WolfTheProblemSolver.getInstance(project).clearProblems(openedFile);
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
    final Editor editor = event.getEditor();
    final Document document = editor.getDocument();
    StudyEditor.removeListener(document);
    editor.getMarkupModel().removeAllHighlighters();
    editor.getSelectionModel().removeSelection();
  }
}
