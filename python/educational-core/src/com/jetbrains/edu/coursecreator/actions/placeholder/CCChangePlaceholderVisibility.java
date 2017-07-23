package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.DocumentUtil;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public abstract class CCChangePlaceholderVisibility extends CCAnswerPlaceholderAction {

  protected CCChangePlaceholderVisibility(@Nullable String text, @Nullable String description) {
    super(text, description);
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    AnswerPlaceholder placeholder = state.getAnswerPlaceholder();
    if (placeholder == null) {
      return;
    }
    EduUtils.runUndoableAction(state.getProject(), getName(), new BasicUndoableAction(state.getEditor().getDocument()) {
      @Override
      public void undo() throws UnexpectedUndoException {
        setVisible(placeholder, isVisible(), state);
      }

      @Override
      public void redo() throws UnexpectedUndoException {
        setVisible(placeholder, !isVisible(), state);
      }
    }, UndoConfirmationPolicy.REQUEST_CONFIRMATION);
  }

  private void setVisible(AnswerPlaceholder placeholder, boolean visible, CCState state) {
    placeholder.getActiveSubtaskInfo().setNeedInsertText(visible);
    saveIndent(placeholder, state, !visible);
    int length = isVisible() ? placeholder.getTaskText().length() : 0;
    placeholder.setLength(length);
    StudyUtils.drawAllAnswerPlaceholders(state.getEditor(), state.getTaskFile());
  }

  private static void saveIndent(AnswerPlaceholder placeholder, CCState state, boolean visible) {
    Document document = state.getEditor().getDocument();
    int offset = placeholder.getOffset();
    int lineNumber = document.getLineNumber(offset);
    int nonSpaceCharOffset = DocumentUtil.getFirstNonSpaceCharOffset(document, lineNumber);
    int newOffset = offset;
    int endOffset = offset + placeholder.getRealLength();
    if (!visible && nonSpaceCharOffset == offset) {
      newOffset = document.getLineStartOffset(lineNumber);
    }
    if (visible) {
      newOffset = DocumentUtil.getFirstNonSpaceCharOffset(document, offset, endOffset);
    }
    placeholder.setOffset(newOffset);
    int delta = offset - newOffset;
    placeholder.setPossibleAnswer(document.getText(TextRange.create(newOffset, newOffset + delta + placeholder.getRealLength())));
    String oldTaskText = placeholder.getTaskText();
    if (delta >= 0) {
      placeholder.setTaskText(StringUtil.repeat(" ", delta) + oldTaskText);
    }
    else {
      String newTaskText = oldTaskText.substring(Math.abs(delta));
      placeholder.setTaskText(newTaskText);
    }
  }

  protected abstract String getName();

  protected abstract boolean isVisible();

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    CCState state = getState(e);
    if (state == null) {
      return;
    }
    AnswerPlaceholder placeholder = state.getAnswerPlaceholder();
    if (placeholder == null) {
      return;
    }
    Task task = state.getTaskFile().getTask();
    if ((task instanceof TaskWithSubtasks)) {
      Integer minSubtaskIndex = Collections.min(placeholder.getSubtaskInfos().keySet());
      if (canChangeState(placeholder, (TaskWithSubtasks)task, minSubtaskIndex)) {
        presentation.setEnabledAndVisible(true);
      }
    }
  }

  private boolean canChangeState(@NotNull AnswerPlaceholder placeholder, @NotNull TaskWithSubtasks task, int minSubtaskIndex) {
    return placeholder.isActive() && minSubtaskIndex != 0 && minSubtaskIndex == task.getActiveSubtaskIndex() && isAvailable(placeholder);
  }

  protected abstract boolean isAvailable(AnswerPlaceholder placeholder);
}
