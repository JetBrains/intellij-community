package com.jetbrains.edu.learning.actions;

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
import com.jetbrains.edu.learning.StudyState;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.statistics.EduUsagesCollector;
import com.jetbrains.edu.learning.ui.StudyHint;
import com.jetbrains.edu.learning.ui.StudyToolWindow;
import icons.InteractiveLearningIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class StudyShowHintAction extends StudyActionWithShortcut {
  public static final String ACTION_ID = "ShowHintAction";
  public static final String SHORTCUT = "ctrl pressed 7";

  public StudyShowHintAction() {
    super("Show hint (" + KeymapUtil.getShortcutText(new KeyboardShortcut(KeyStroke.getKeyStroke(SHORTCUT), null)) + ")", "Show hint",
          InteractiveLearningIcons.ShowHint);
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
    AnswerPlaceholder answerPlaceholder = studyState.getTaskFile().getAnswerPlaceholder(offset);
    if (file == null) {
      return;
    }
    EduUsagesCollector.hintShown();

    final StudyToolWindow hintComponent = new StudyHint(answerPlaceholder, project).getStudyToolWindow();
    hintComponent.setPreferredSize(new Dimension(400, 150));
    showHintPopUp(project, studyState, editor, hintComponent);
  }

  private static void showHintPopUp(Project project, StudyState studyState, Editor editor, StudyToolWindow hintComponent) {
    final JBPopup popup =
      JBPopupFactory.getInstance().createComponentPopupBuilder(hintComponent, hintComponent)
        .setDimensionServiceKey(project, "StudyHint", false)
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
    final Project project = e.getProject();
    if (project != null) {
      final Course course = StudyTaskManager.getInstance(project).getCourse();
      if (course != null && course.isAdaptive()) {
        e.getPresentation().setEnabled(false);
        return;
      }
    }
    
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
