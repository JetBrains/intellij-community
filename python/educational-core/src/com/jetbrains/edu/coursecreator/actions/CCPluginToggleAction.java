package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.jetbrains.edu.learning.StudySettings;

public class CCPluginToggleAction extends ToggleAction {
  @Override
  public boolean isSelected(AnActionEvent e) {
    return StudySettings.getInstance().isCourseCreatorEnabled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    StudySettings.getInstance().setCourseCreatorEnabled(state);
  }
}