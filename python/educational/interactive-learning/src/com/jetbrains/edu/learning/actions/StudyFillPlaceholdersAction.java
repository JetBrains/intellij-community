package com.jetbrains.edu.learning.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
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
      final TaskFile taskFile = studyState.getTaskFile();
      final Document document = studyState.getEditor().getDocument();
      CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              for (AnswerPlaceholder placeholder : taskFile.getAnswerPlaceholders()) {
                String answer = placeholder.getPossibleAnswer();
                if (answer == null) {
                  continue;
                }
                int offset = placeholder.getRealStartOffset(document);
                document.deleteString(offset, offset + placeholder.getLength());
                document.insertString(offset, answer);
              }
            }
          });
        }
      });
    }
  }

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
    final Project project = e.getProject();
    if (project != null) {
      StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
      StudyState studyState = new StudyState(studyEditor);
      if (!studyState.isValid()) {
        e.getPresentation().setEnabledAndVisible(false);
        return;
      }
      TaskFile taskFile = studyState.getTaskFile();
      if (taskFile.getAnswerPlaceholders().isEmpty()) {
        e.getPresentation().setEnabledAndVisible(false);
      }
    }
  }
}