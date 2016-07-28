package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.jetbrains.edu.coursecreator.CCUtils;

public class CCFileActionGroup extends DefaultActionGroup {
  @Override
  public void update(AnActionEvent e) {
    CCUtils.updateActionGroup(e);
  }
}
