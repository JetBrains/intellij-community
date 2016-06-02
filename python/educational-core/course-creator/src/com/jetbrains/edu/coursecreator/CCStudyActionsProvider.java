package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.actionSystem.AnAction;
import com.jetbrains.edu.coursecreator.actions.CCEditTaskTextAction;
import com.jetbrains.edu.learning.StudyActionsProvider;

public class CCStudyActionsProvider implements StudyActionsProvider{
  @Override
  public AnAction[] getActions() {
    return new AnAction[]{new CCEditTaskTextAction()};
  }
}
