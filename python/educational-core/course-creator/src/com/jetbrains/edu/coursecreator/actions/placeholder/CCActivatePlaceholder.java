package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.intellij.openapi.util.TextRange;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.TaskFile;

public class CCActivatePlaceholder extends CCSubtaskPlaceholderAction {

  public static final String TITLE = "Activate Answer Placeholder";

  protected CCActivatePlaceholder() {
    super(TITLE, TITLE);
  }

  @Override
  protected AnswerPlaceholderSubtaskInfo getInfo(CCState state,
                                                 int subtaskIndex,
                                                 AnswerPlaceholder existingPlaceholder) {
    int visibleLength = existingPlaceholder.getVisibleLength(subtaskIndex);
    int placeholderOffset = existingPlaceholder.getOffset();
    String possibleAnswer = state.getEditor().getDocument().getText(TextRange.create(placeholderOffset, placeholderOffset + visibleLength));
    AnswerPlaceholderSubtaskInfo info = new AnswerPlaceholderSubtaskInfo();
    info.setPossibleAnswer(possibleAnswer);
    return info;
  }

  @Override
  protected String getTitle() {
    return TITLE;
  }

  @Override
  protected void redoAction(AnswerPlaceholder existingPlaceholder, int subtaskIndex, AnswerPlaceholderSubtaskInfo info) {
    existingPlaceholder.getSubtaskInfos().put(subtaskIndex, info);
  }

  @Override
  protected void undoAction(AnswerPlaceholder existingPlaceholder, int subtaskIndex, AnswerPlaceholderSubtaskInfo info) {
    existingPlaceholder.getSubtaskInfos().remove(subtaskIndex);
  }

  @Override
  protected boolean isAvailable(TaskFile taskFile, int offset) {
    AnswerPlaceholder existingPlaceholder = taskFile.getAnswerPlaceholder(offset, taskFile.getAnswerPlaceholders());
    return existingPlaceholder != null && !existingPlaceholder.isActive();
  }
}
