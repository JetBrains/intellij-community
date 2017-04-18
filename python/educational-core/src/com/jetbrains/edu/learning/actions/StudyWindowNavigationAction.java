package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract public class StudyWindowNavigationAction extends StudyActionWithShortcut implements DumbAware {

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
          final int offset = selectedEditor.getCaretModel().getOffset();
          final AnswerPlaceholder targetPlaceholder = getTargetPlaceholder(selectedTaskFile, offset);
          if (targetPlaceholder == null) {
            return;
          }
          StudyNavigator.navigateToAnswerPlaceholder(selectedEditor, targetPlaceholder);
        }
      }
    }
  }

  @Nullable
  protected abstract AnswerPlaceholder getTargetPlaceholder(@NotNull final TaskFile taskFile, int offset);

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
