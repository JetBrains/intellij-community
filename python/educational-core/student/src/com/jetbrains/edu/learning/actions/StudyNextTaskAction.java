package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class StudyNextTaskAction extends StudyTaskNavigationAction {

  public static final String ACTION_ID = "NextTaskAction";
  public static final String SHORTCUT = "ctrl pressed PERIOD";

  public StudyNextTaskAction() {
    super("Next Task (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")",
          "Navigate to the next task", AllIcons.Actions.Forward);
  }

  @Override
  protected Task getTargetTask(@NotNull final Task sourceTask) {
    return StudyNavigator.nextTask(sourceTask);
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