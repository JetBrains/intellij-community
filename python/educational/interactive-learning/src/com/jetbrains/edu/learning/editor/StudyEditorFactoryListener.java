package com.jetbrains.edu.learning.editor;


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
import com.intellij.ui.JBColor;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.EduDocumentListener;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;

import java.awt.*;


public class StudyEditorFactoryListener implements EditorFactoryListener {

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
      final AnswerPlaceholder answerPlaceholder = myTaskFile.getAnswerPlaceholder(editor.getDocument(), pos);
      if (answerPlaceholder != null) {
        myTaskFile.setSelectedAnswerPlaceholder(answerPlaceholder);
        final Project project = editor.getProject();
        assert project != null;
        final JBColor color = StudyTaskManager.getInstance(project).getColor(answerPlaceholder);
        EduAnswerPlaceholderPainter.drawAnswerPlaceholder(editor, answerPlaceholder, true, color);
      }
      else {
        StudyUtils.drawAllWindows(editor, myTaskFile);
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
                final TaskFile taskFile = StudyUtils.getTaskFile(project, openedFile);
                if (taskFile != null) {
                  StudyNavigator.navigateToFirstAnswerPlaceholder(editor, taskFile);
                  editor.addEditorMouseListener(new WindowSelectionListener(taskFile));
                  StudyEditor.addDocumentListener(document, new EduDocumentListener(taskFile));
                  WolfTheProblemSolver.getInstance(project).clearProblems(openedFile);
                  StudyUtils.drawAllWindows(editor, taskFile);
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
