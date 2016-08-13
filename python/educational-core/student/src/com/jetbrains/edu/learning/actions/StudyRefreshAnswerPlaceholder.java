package com.jetbrains.edu.learning.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.edu.learning.StudyActionListener;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
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
    for (StudyActionListener listener : Extensions.getExtensions(StudyActionListener.EP_NAME)) {
      listener.beforeCheck(e);
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
    int startOffset = initialState.getOffset();
    final String text = patternDocument.getText(new TextRange(startOffset, startOffset + initialState.getLength()));
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      Document document = studyState.getEditor().getDocument();
      int offset = answerPlaceholder.getOffset();
      document.deleteString(offset, offset + answerPlaceholder.getRealLength());
      document.insertString(offset, text);
    }), NAME, null);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }

    if (!EduNames.STUDY.equals(course.getCourseMode())) {
      presentation.setVisible(true);
      return;
    }

    if (getAnswerPlaceholder(e) == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    presentation.setEnabledAndVisible(true);
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
    return taskFile.getAnswerPlaceholder(editor.getCaretModel().getOffset());
  }
}
