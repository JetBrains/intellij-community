package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.TaskFile;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.editor.StudyEditor;
import org.jetbrains.annotations.Nullable;

public class StudyRefreshAnswerPlaceholder extends DumbAwareAction {

  public static final String NAME = "Refresh Answer Placeholder";

  public StudyRefreshAnswerPlaceholder() {
    super(NAME, NAME, AllIcons.Actions.Refresh);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    final AnswerPlaceholder answerPlaceholder = getAnswerPlaceholder(e);
    if (answerPlaceholder == null) {
      return;
    }
    StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    final StudyState studyState = new StudyState(studyEditor);
    Document patternDocument = StudyUtils.getPatternDocument(answerPlaceholder.getTaskFile(), studyState.getVirtualFile().getName());
    if (patternDocument == null) {
      return;
    }
    AnswerPlaceholder.MyInitialState initialState = answerPlaceholder.getInitialState();
    int startOffset = patternDocument.getLineStartOffset(initialState.myLine) + initialState.myStart;
    final String text = patternDocument.getText(new TextRange(startOffset, startOffset + initialState.myLength));
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            Document document = studyState.getEditor().getDocument();
            int offset = answerPlaceholder.getRealStartOffset(document);
            document.deleteString(offset, offset + answerPlaceholder.getLength());
            document.insertString(offset, text);
          }
        });
      }
    }, NAME, null);
  }

  @Override
  public void update(AnActionEvent e) {
    if (getAnswerPlaceholder(e) == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Nullable
  private static AnswerPlaceholder getAnswerPlaceholder(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return null;
    }
    StudyEditor studyEditor = StudyUtils.getSelectedStudyEditor(project);
    final StudyState studyState = new StudyState(studyEditor);
    if (studyEditor == null || !studyState.isValid()) {
      return null;
    }
    final Editor editor = studyState.getEditor();
    final TaskFile taskFile = studyState.getTaskFile();
    return taskFile.getAnswerPlaceholder(editor.getDocument(), editor.getCaretModel().getLogicalPosition());
  }
}
