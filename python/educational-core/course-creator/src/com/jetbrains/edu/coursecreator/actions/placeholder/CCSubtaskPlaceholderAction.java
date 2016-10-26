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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CCSubtaskPlaceholderAction extends CCAnswerPlaceholderAction {
  protected CCSubtaskPlaceholderAction(@Nullable String text, @Nullable String description) {
    super(text, description);
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    Editor editor = state.getEditor();
    final int offset = editor.getCaretModel().getOffset();
    TaskFile taskFile = state.getTaskFile();
    int subtaskIndex = state.getTaskFile().getTask().getActiveSubtaskIndex();
    AnswerPlaceholder existingPlaceholder = taskFile.getAnswerPlaceholder(offset, taskFile.getAnswerPlaceholders());
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
        StudyUtils.drawAllWindows(editor, taskFile);
      }

      @Override
      public void redo() throws UnexpectedUndoException {
        redoAction(existingPlaceholder, subtaskIndex, info);
        StudyUtils.drawAllWindows(editor, taskFile);
      }
    });
  }

  protected abstract AnswerPlaceholderSubtaskInfo getInfo(CCState state, int subtaskIndex, AnswerPlaceholder existingPlaceholder);

  protected abstract String getTitle();

  protected abstract void redoAction(AnswerPlaceholder existingPlaceholder, int subtaskIndex, AnswerPlaceholderSubtaskInfo info);

  protected abstract void undoAction(AnswerPlaceholder existingPlaceholder, int subtaskIndex, AnswerPlaceholderSubtaskInfo info);

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    CCState state = getState(e);
    if (state == null) {
      return;
    }
    TaskFile taskFile = state.getTaskFile();
    if (!taskFile.getTask().hasSubtasks()) {
      return;
    }
    int offset = state.getEditor().getCaretModel().getOffset();
    if (isAvailable(taskFile, offset)) {
      presentation.setEnabledAndVisible(true);
    }
  }

  protected abstract boolean isAvailable(TaskFile taskFile, int offset);
}
