package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyNextStudyTaskAction extends StudyTaskNavigationAction {

  public static final String ACTION_ID = "NextTaskAction";
  public static final String SHORTCUT = "ctrl pressed PERIOD";

  public StudyNextStudyTaskAction() {
    super("Next Task (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Navigate to the next task", AllIcons.Actions.Forward);
  }

  @Override
  protected String getNavigationFinishedMessage() {
    return "It's the last task";
  }

  @Override
  protected Task getTargetTask(@NotNull final Task sourceTask) {
    return StudyNavigator.nextTask(sourceTask);
  }
}