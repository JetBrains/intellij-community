package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract public class StudyWindowNavigationAction extends DumbAwareAction {

  protected StudyWindowNavigationAction(String actionId, String description, Icon icon) {
    super(actionId, description, icon);
  }

  private void navigateToPlaceholder(@NotNull final Project project) {
      final Editor selectedEditor = StudyUtils.getSelectedEditor(project);
      if (selectedEditor != null) {
        final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        final VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
        if (openedFile != null) {
          final TaskFile selectedTaskFile = StudyUtils.getTaskFile(project, openedFile);
          if (selectedTaskFile != null) {
            final AnswerPlaceholder selectedAnswerPlaceholder = getSelectedAnswerPlaceholder(selectedEditor, selectedTaskFile);
            if (selectedAnswerPlaceholder == null) {
              return;
            }
            final AnswerPlaceholder nextAnswerPlaceholder = getNextAnswerPlaceholder(selectedAnswerPlaceholder);
            if (nextAnswerPlaceholder == null) {
              return;
            }
            StudyNavigator.navigateToAnswerPlaceholder(selectedEditor, nextAnswerPlaceholder, selectedTaskFile);
            selectedEditor.getSelectionModel().removeSelection();
            }
          }
        }
      }

  @Nullable
  private static AnswerPlaceholder getSelectedAnswerPlaceholder(@NotNull final Editor editor, @NotNull final TaskFile file) {
    LogicalPosition position = editor.getCaretModel().getLogicalPosition();
    return file.getAnswerPlaceholder(editor.getDocument(), position);
  }

  @Nullable
  protected abstract AnswerPlaceholder getNextAnswerPlaceholder(@NotNull final AnswerPlaceholder window);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    navigateToPlaceholder(project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
