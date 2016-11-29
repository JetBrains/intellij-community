package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAware;

public class CCAnswerPlaceholderActionGroup extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    CCUtils.updateActionGroup(e);
  }
}
