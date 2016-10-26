package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Task;
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
    });
  }

  private void setVisible(AnswerPlaceholder placeholder, boolean visible, CCState state) {
    placeholder.getActiveSubtaskInfo().setNeedInsertText(visible);
    int length = isVisible() ? placeholder.getTaskText().length() : 0;
    placeholder.setLength(length);
    StudyUtils.drawAllWindows(state.getEditor(), state.getTaskFile());
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
    if (!task.hasSubtasks()) {
      return;
    }
    Integer minSubtaskIndex = Collections.min(placeholder.getSubtaskInfos().keySet());
    if (placeholder.isActive() && minSubtaskIndex != 0 && minSubtaskIndex == task.getActiveSubtaskIndex() && isAvailable(placeholder)) {
      presentation.setEnabledAndVisible(true);
    }
  }

  protected abstract boolean isAvailable(AnswerPlaceholder placeholder);
}
