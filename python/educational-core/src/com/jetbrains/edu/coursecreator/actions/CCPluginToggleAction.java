package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

public class CCPluginToggleAction extends ToggleAction {
  public static final String COURSE_CREATOR_ENABLED = "Edu.CourseCreator.Enabled";
  @Override
  public boolean isSelected(AnActionEvent e) {
    return PropertiesComponent.getInstance().getBoolean(COURSE_CREATOR_ENABLED);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    PropertiesComponent.getInstance().setValue(COURSE_CREATOR_ENABLED, state);
  }
}