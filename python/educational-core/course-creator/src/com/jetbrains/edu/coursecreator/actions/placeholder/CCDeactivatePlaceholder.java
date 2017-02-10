package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class CCDeactivatePlaceholder extends CCSubtaskPlaceholderAction {

  public static final String TITLE = "Deactivate";

  protected CCDeactivatePlaceholder() {
    super(TITLE, TITLE);
  }

  protected void undoAction(@NotNull AnswerPlaceholder existingPlaceholder, int subtaskIndex, @NotNull AnswerPlaceholderSubtaskInfo info) {
    existingPlaceholder.getSubtaskInfos().put(subtaskIndex, info);
  }

  @Override
  protected AnswerPlaceholderSubtaskInfo getInfo(@NotNull CCState state,
                                                 int subtaskIndex,
                                                 @NotNull AnswerPlaceholder existingPlaceholder) {
    return existingPlaceholder.getSubtaskInfos().get(subtaskIndex);
  }

  @Override
  protected String getTitle() {
    return TITLE;
  }

  protected void redoAction(@NotNull AnswerPlaceholder existingPlaceholder, int subtaskIndex, @NotNull AnswerPlaceholderSubtaskInfo info) {
    existingPlaceholder.getSubtaskInfos().remove(subtaskIndex);
  }

  protected boolean isAvailable(TaskFile taskFile, int offset) {
    AnswerPlaceholder existingActivePlaceholder = taskFile.getAnswerPlaceholder(offset);
    if (existingActivePlaceholder == null) {
      return false;
    }
    return Collections.min(existingActivePlaceholder.getSubtaskInfos().keySet()) < taskFile.getTask().getActiveSubtaskIndex();
  }
}
