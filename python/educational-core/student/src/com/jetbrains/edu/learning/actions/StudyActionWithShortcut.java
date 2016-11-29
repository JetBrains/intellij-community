package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class StudyActionWithShortcut extends DumbAwareAction {

  public StudyActionWithShortcut(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  @NotNull
  public abstract String getActionId();
  
  @Nullable
  public abstract String[] getShortcuts();
}