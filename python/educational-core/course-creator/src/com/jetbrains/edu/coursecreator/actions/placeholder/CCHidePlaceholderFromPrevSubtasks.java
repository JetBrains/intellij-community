package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;

public class CCHidePlaceholderFromPrevSubtasks extends CCChangePlaceholderVisibility {

  public static final String TITLE = "Hide for Previous Subtasks";

  public CCHidePlaceholderFromPrevSubtasks() {
    super(TITLE, TITLE);
  }

  @Override
  protected String getName() {
    return TITLE;
  }

  @Override
  protected boolean isVisible() {
    return false;
  }

  @Override
  protected boolean isAvailable(AnswerPlaceholder placeholder) {
    return !placeholder.getActiveSubtaskInfo().isNeedInsertText();
  }
}
