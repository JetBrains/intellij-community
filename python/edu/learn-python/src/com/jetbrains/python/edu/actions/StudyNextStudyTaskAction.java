package com.jetbrains.python.edu.actions;

import com.jetbrains.python.edu.editor.StudyEditor;
import com.jetbrains.python.edu.course.Task;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyNextStudyTaskAction extends StudyTaskNavigationAction {

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