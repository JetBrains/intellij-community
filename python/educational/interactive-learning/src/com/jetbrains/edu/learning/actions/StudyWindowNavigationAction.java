package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.course.TaskFile;
import com.jetbrains.edu.learning.course.AnswerPlaceholder;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract public class StudyWindowNavigationAction extends DumbAwareAction {

  protected StudyWindowNavigationAction(String actionId, String description, Icon icon) {
    super(actionId, description, icon);
  }

  private void navigateToPlaceholder(@NotNull final Project project) {
      final Editor selectedEditor = StudyEditor.getSelectedEditor(project);
      if (selectedEditor != null) {
        final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        final VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
        if (openedFile != null) {
          final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
          final TaskFile selectedTaskFile = taskManager.getTaskFile(openedFile);
          if (selectedTaskFile != null) {
            final AnswerPlaceholder selectedAnswerPlaceholder = selectedTaskFile.getSelectedAnswerPlaceholder();
            if (selectedAnswerPlaceholder == null) {
              return;
            }
            final AnswerPlaceholder nextAnswerPlaceholder = getNextTaskWindow(selectedAnswerPlaceholder);
            if (nextAnswerPlaceholder == null) {
              return;
            }
            selectedTaskFile.navigateToTaskWindow(selectedEditor, nextAnswerPlaceholder);
            selectedTaskFile.setSelectedAnswerPlaceholder(nextAnswerPlaceholder);
            }
          }
        }
      }

  @Nullable
  protected abstract AnswerPlaceholder getNextTaskWindow(@NotNull final AnswerPlaceholder window);

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
