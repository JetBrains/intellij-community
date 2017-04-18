package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;

public class CCMakeVisibleForPrevSubtasks extends CCChangePlaceholderVisibility {

  public static final String TITLE = "Make Visible For Previous Subtasks";

  protected CCMakeVisibleForPrevSubtasks() {
    super(TITLE, TITLE);
  }

  @Override
  protected String getName() {
    return TITLE;
  }

  @Override
  protected boolean isVisible() {
    return true;
  }

  @Override
  protected boolean isAvailable(AnswerPlaceholder placeholder) {
    return placeholder.getActiveSubtaskInfo().isNeedInsertText();
  }
}
