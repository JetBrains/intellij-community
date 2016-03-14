package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class StudyToolbarAction extends AnAction {
  public StudyToolbarAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public abstract String getActionId();
  
  public abstract String[] getShortcuts();  
}
