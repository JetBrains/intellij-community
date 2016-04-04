package com.jetbrains.edu.learning.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.edu.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StudyShowHintAction extends DumbAwareAction {
  public static final String ACTION_ID = "ShowHintAction";
  public static final String SHORTCUT = "ctrl pressed 7";
  private static final String ourWarningMessage = "Put the caret in the answer placeholder to get hint";
  public static final String HINT_NOT_AVAILABLE = "There is no hint for this answer placeholder";

  public StudyShowHintAction() {
    super("Show hint (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Show hint", InteractiveLearningIcons.ShowHint);
  }

  public void actionPerformed(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    showHint(project);
  }

  public static void showHint(Project project) {
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    StudyState studyState = new StudyState(StudyUtils.getSelectedStudyEditor(project));
    if (!studyState.isValid()) {
      return;
    }
    PsiFile file = PsiManager.getInstance(project).findFile(studyState.getVirtualFile());
    final Editor editor = studyState.getEditor();
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    AnswerPlaceholder answerPlaceholder = studyState.getTaskFile().getAnswerPlaceholder(editor.getDocument(), pos);
    if (file == null) {
      return;
    }
    String hintText = ourWarningMessage;
    if (answerPlaceholder != null) {
      String hint = answerPlaceholder.getHint();
      hintText = hint.isEmpty() ? HINT_NOT_AVAILABLE : hint;
    }
    int offset = editor.getDocument().getLineStartOffset(pos.line) + pos.column;
    PsiElement element = file.findElementAt(offset);
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    DocumentationComponent component = new DocumentationComponent(documentationManager);
    component.setData(element != null ? element : file, element != null ? hintText : ourWarningMessage, true, null);
    showHintPopUp(project, editor, component);
  }

  private static void showHintPopUp(Project project, Editor editor, DocumentationComponent component) {
    final JBPopup popup =
      JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .createPopup();
    component.setHint(popup);
    popup.showInBestPositionFor(editor);
    Disposer.dispose(component);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
