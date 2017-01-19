package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCDeleteAnswerPlaceholder extends CCAnswerPlaceholderAction {
  public CCDeleteAnswerPlaceholder() {
    super("Delete", "Delete answer placeholder");
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    deletePlaceholder(state);
  }

  private static void deletePlaceholder(@NotNull CCState state) {
    Project project = state.getProject();
    TaskFile taskFile = state.getTaskFile();
    AnswerPlaceholder answerPlaceholder = state.getAnswerPlaceholder();
    EduUtils.runUndoableAction(project, "Delete Answer Placeholder",
                               new CCAddAnswerPlaceholder.AddAction(answerPlaceholder, taskFile, state.getEditor()) {
                                 @Override
                                 public void undo() throws UnexpectedUndoException {
                                   super.redo();
                                 }

                                 @Override
                                 public void redo() throws UnexpectedUndoException {
                                   super.undo();
                                 }
                               });
  }

  private static boolean canDeletePlaceholder(@NotNull CCState state) {
    if (state.getEditor().getSelectionModel().hasSelection()) {
      return false;
    }
    return state.getAnswerPlaceholder() != null;
  }

  @Override
  protected List<AnswerPlaceholder> getPlaceholders(@NotNull TaskFile taskFile) {
    return taskFile.getAnswerPlaceholders();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setEnabledAndVisible(false);

    CCState state = getState(event);
    if (state == null) {
      return;
    }

    if (canDeletePlaceholder(state)) {
      presentation.setEnabledAndVisible(true);
    }
  }
}
