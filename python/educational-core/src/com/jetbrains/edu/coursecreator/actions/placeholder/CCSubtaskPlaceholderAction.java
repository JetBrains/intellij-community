package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Editor;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CCSubtaskPlaceholderAction extends CCAnswerPlaceholderAction {
  protected CCSubtaskPlaceholderAction(@Nullable String text, @Nullable String description) {
    super(text, description);
  }

  @Override
  public void performAnswerPlaceholderAction(@NotNull CCState state) {
    Editor editor = state.getEditor();
    final int offset = editor.getCaretModel().getOffset();
    TaskFile taskFile = state.getTaskFile();
    final Task task = state.getTaskFile().getTask();
    if (!(task instanceof TaskWithSubtasks)) return;

    int subtaskIndex = ((TaskWithSubtasks)task).getActiveSubtaskIndex();
    AnswerPlaceholder existingPlaceholder = StudyUtils.getAnswerPlaceholder(offset, taskFile.getAnswerPlaceholders());
    if (existingPlaceholder == null) {
      return;
    }
    AnswerPlaceholderSubtaskInfo info = getInfo(state, subtaskIndex, existingPlaceholder);
    if (info == null) {
      return;
    }
    EduUtils.runUndoableAction(state.getProject(), getTitle(), new BasicUndoableAction(state.getEditor().getDocument()) {
      @Override
      public void undo() throws UnexpectedUndoException {
        undoAction(existingPlaceholder, subtaskIndex, info);
        StudyUtils.drawAllAnswerPlaceholders(editor, taskFile);
      }

      @Override
      public void redo() throws UnexpectedUndoException {
        redoAction(existingPlaceholder, subtaskIndex, info);
        StudyUtils.drawAllAnswerPlaceholders(editor, taskFile);
      }
    });
  }

  protected abstract AnswerPlaceholderSubtaskInfo getInfo(@NotNull CCState state, int subtaskIndex, @NotNull AnswerPlaceholder existingPlaceholder);

  protected abstract String getTitle();

  protected abstract void redoAction(@NotNull AnswerPlaceholder existingPlaceholder, int subtaskIndex, @NotNull AnswerPlaceholderSubtaskInfo info);

  protected abstract void undoAction(@NotNull AnswerPlaceholder existingPlaceholder, int subtaskIndex, @NotNull AnswerPlaceholderSubtaskInfo info);

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    CCState state = getState(e);
    if (state == null) {
      return;
    }
    TaskFile taskFile = state.getTaskFile();
    if (taskFile.getTask() instanceof TaskWithSubtasks) {
      int offset = state.getEditor().getCaretModel().getOffset();
      if (isAvailable(taskFile, offset)) {
        presentation.setEnabledAndVisible(true);
      }
    }
  }

  protected abstract boolean isAvailable(TaskFile taskFile, int offset);
}
