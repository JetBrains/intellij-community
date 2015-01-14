package com.jetbrains.edu.learning.actions;

import com.jetbrains.edu.learning.course.Task;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyNextStudyTaskAction extends StudyTaskNavigationAction {

  public static final String ACTION_ID = "NextTaskAction";
  public static final String SHORTCUT = "ctrl pressed PERIOD";

  @Override
  protected JButton getButton(@NotNull final StudyEditor selectedStudyEditor) {
    return selectedStudyEditor.getNextTaskButton();
  }

  @Override
  protected String getNavigationFinishedMessage() {
    return "It's the last task";
  }

  @Override
  protected Task getTargetTask(@NotNull final Task sourceTask) {
    return sourceTask.next();
  }
}