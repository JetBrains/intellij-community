package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.editor.StudyEditor;
import com.jetbrains.edu.learning.navigation.StudyNavigator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


abstract public class StudyTaskNavigationAction extends StudyActionWithShortcut {
  public StudyTaskNavigationAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public void navigateTask(@NotNull final Project project) {
    StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    StudyState studyState = new StudyState(studyEditor);
    if (!studyState.isValid()) {
      return;
    }
    Task targetTask = getTargetTask(studyState.getTask());
    if (targetTask == null) {
      return;
    }

    StudyNavigator.navigateToTask(project, targetTask);

  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    navigateTask(project);
  }

  protected abstract Task getTargetTask(@NotNull final Task sourceTask);

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    StudyState studyState = new StudyState(studyEditor);
    if (!studyState.isValid()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    if (getTargetTask(studyState.getTask()) == null) {
      e.getPresentation().setEnabled(false);
    }
  }
}
