package com.jetbrains.edu.learning.actions;


import com.jetbrains.edu.learning.course.Task;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyPreviousStudyTaskAction extends StudyTaskNavigationAction {

  public static final String ACTION_ID = "PreviousTaskAction";
  public static final String SHORTCUT = "ctrl pressed COMMA";
  @Override
  protected JButton getButton(@NotNull final StudyEditor selectedStudyEditor) {
    return selectedStudyEditor.getPrevTaskButton();
  }

  @Override
  protected String getNavigationFinishedMessage() {
    return "It's already the first task";
  }

  @Override
  protected Task getTargetTask(@NotNull final Task sourceTask) {
    return sourceTask.prev();
  }
}