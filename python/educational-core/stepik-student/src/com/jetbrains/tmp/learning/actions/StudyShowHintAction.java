package com.jetbrains.tmp.learning.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.popup.PopupPositionManager;
import com.jetbrains.tmp.learning.StudyState;
import com.jetbrains.tmp.learning.StudyTaskManager;
import com.jetbrains.tmp.learning.StudyUtils;
import com.jetbrains.tmp.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.tmp.learning.courseFormat.Course;
import com.jetbrains.tmp.learning.statistics.EduUsagesCollector;
import com.jetbrains.tmp.learning.ui.StudyHint;
import com.jetbrains.tmp.learning.ui.StudyToolWindow;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

public class StudyShowHintAction extends StudyActionWithShortcut {
  public static final String ACTION_ID = "SCore.ShowHintAction";
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
    int offset = editor.getCaretModel().getOffset();
    AnswerPlaceholder answerPlaceholder = studyState.getTaskFile().getAnswerPlaceholder(
      offset);
    if (file == null) {
      return;
    }
    EduUsagesCollector.hintShown();
    LinkedList<String> hints = new LinkedList<>();
    if (answerPlaceholder != null) {
      hints.addAll(answerPlaceholder.getHints());
      //final ArrayList<String> strings = new ArrayList<>();
      //strings.add(answerPlaceholder.getHints());
      //strings.add("test");
      //hints.addAll(strings);
    }
    else {
      hints.add(ourWarningMessage);
    }
    final StudyToolWindow hintComponent = new StudyHint(answerPlaceholder, project).getStudyToolWindow();

    showHintPopUp(project, studyState, editor, hintComponent);
  }

  private static void showHintPopUp(Project project, StudyState studyState, Editor editor, StudyToolWindow hintComponent) {
    final JBPopup popup = 
      JBPopupFactory.getInstance().createComponentPopupBuilder(hintComponent, hintComponent)
        .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
        .setResizable(true)
        .setMovable(true)
        .setRequestFocus(true)
        .setTitle(studyState.getTask().getName())
        .createPopup();
    Disposer.register(popup, hintComponent);

    final Component focusOwner = IdeFocusManager.getInstance(project).getFocusOwner();
    DataContext dataContext = DataManager.getInstance().getDataContext(focusOwner);
    PopupPositionManager.positionPopupInBestPosition(popup, editor, dataContext);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    StudyUtils.updateAction(e);
  }

  @NotNull
  @Override
  public String getActionId() {
    return ACTION_ID;
  }

  @Nullable
  @Override
  public String[] getShortcuts() {
    return new String[]{SHORTCUT};
  }
}
