package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.TaskFile;

import java.util.Collections;

public class CCDeactivatePlaceholder extends CCSubtaskPlaceholderAction {

  public static final String TITLE = "Deactivate Answer Placeholder";

  protected CCDeactivatePlaceholder() {
    super(TITLE, TITLE);
  }

  protected void undoAction(AnswerPlaceholder existingPlaceholder, int subtaskIndex, AnswerPlaceholderSubtaskInfo info) {
    existingPlaceholder.getSubtaskInfos().put(subtaskIndex, info);
  }

  @Override
  protected AnswerPlaceholderSubtaskInfo getInfo(CCState state,
                                                 int subtaskIndex,
                                                 AnswerPlaceholder existingPlaceholder) {
    return existingPlaceholder.getSubtaskInfos().get(subtaskIndex);
  }

  @Override
  protected String getTitle() {
    return TITLE;
  }

  protected void redoAction(AnswerPlaceholder existingPlaceholder, int subtaskIndex, AnswerPlaceholderSubtaskInfo info) {
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
