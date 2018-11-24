package com.jetbrains.edu.learning.actions;


import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StudyPreviousTaskAction extends StudyTaskNavigationAction {
  public StudyPreviousTaskAction() {
    super("Previous Task (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")",
          "Navigate to the previous task", AllIcons.Actions.Back);
  }

  public static final String ACTION_ID = "PreviousTaskAction";
  public static final String SHORTCUT = "ctrl pressed COMMA";

  @Override
  protected Task getTargetTask(@NotNull final Task sourceTask) {
    return StudyNavigator.previousTask(sourceTask);
  }

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Nullable
  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }
}