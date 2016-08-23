package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.editor.StudyEditor;

public class StudyFillPlaceholdersAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project != null) {
      StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
      StudyState studyState = new StudyState(studyEditor);
      if (!studyState.isValid()) {
        return;
      }
      TaskFile taskFile = studyState.getTaskFile();
      final Document document = studyState.getEditor().getDocument();
      final TaskFile realTaskFile = taskFile;
      CommandProcessor.getInstance().runUndoTransparentAction(() -> ApplicationManager.getApplication().runWriteAction(() -> {
        for (AnswerPlaceholder placeholder : realTaskFile.getAnswerPlaceholders()) {
          String answer = placeholder.getPossibleAnswer();
          if (answer == null) {
            continue;
          }
          int offset = placeholder.getOffset();
          document.deleteString(offset, offset + placeholder.getRealLength());
          document.insertString(offset, answer);
        }
      }));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
    final Project project = e.getProject();
    if (project != null) {

      Course course = StudyTaskManager.getInstance(project).getCourse();
      Presentation presentation = e.getPresentation();
      if (course != null && !EduNames.STUDY.equals(course.getCourseMode())) {
        presentation.setEnabled(false);
        presentation.setVisible(true);
        return;
      }
      StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
      StudyState studyState = new StudyState(studyEditor);
      if (!studyState.isValid()) {
        presentation.setEnabledAndVisible(false);
        return;
      }
      TaskFile taskFile = studyState.getTaskFile();
      if (taskFile.getAnswerPlaceholders().isEmpty()) {
        presentation.setEnabledAndVisible(false);
      }
    }
  }
}