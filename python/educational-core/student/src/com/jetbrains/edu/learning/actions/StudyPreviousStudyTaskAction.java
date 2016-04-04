package com.jetbrains.edu.learning.actions;


import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.jetbrains.edu.courseFormat.Task;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyPreviousStudyTaskAction extends StudyTaskNavigationAction {
  public StudyPreviousStudyTaskAction() {
    super("Previous Task (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Navigate to the previous task", InteractiveLearningIcons.Prev);
  }

  public static final String ACTION_ID = "PreviousTaskAction";
  public static final String SHORTCUT = "ctrl pressed COMMA";

  @Override
  protected String getNavigationFinishedMessage() {
    return "It's already the first task";
  }

  @Override
  protected Task getTargetTask(@NotNull final Task sourceTask) {
    return StudyNavigator.previousTask(sourceTask);
  }
}