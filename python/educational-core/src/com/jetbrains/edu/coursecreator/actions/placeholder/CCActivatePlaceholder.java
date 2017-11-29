package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.intellij.openapi.util.TextRange;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

public class CCActivatePlaceholder extends CCSubtaskPlaceholderAction {

  public static final String TITLE = "Activate";
  public static final String ACTION_ID = "CC.ActivatePlaceholder";

  protected CCActivatePlaceholder() {
    super(TITLE, TITLE);
  }

  @Override
  protected AnswerPlaceholderSubtaskInfo getInfo(@NotNull CCState state,
                                                 int subtaskIndex,
                                                 @NotNull AnswerPlaceholder existingPlaceholder) {
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
  protected void redoAction(@NotNull AnswerPlaceholder existingPlaceholder, int subtaskIndex, @NotNull AnswerPlaceholderSubtaskInfo info) {
    existingPlaceholder.getSubtaskInfos().put(subtaskIndex, info);
  }

  @Override
  protected void undoAction(@NotNull AnswerPlaceholder existingPlaceholder, int subtaskIndex, @NotNull AnswerPlaceholderSubtaskInfo info) {
    existingPlaceholder.getSubtaskInfos().remove(subtaskIndex);
  }

  @Override
  protected boolean isAvailable(TaskFile taskFile, int offset) {
    AnswerPlaceholder existingPlaceholder = StudyUtils.getAnswerPlaceholder(offset, taskFile.getAnswerPlaceholders());
    return existingPlaceholder != null && !existingPlaceholder.isActive();
  }
}
