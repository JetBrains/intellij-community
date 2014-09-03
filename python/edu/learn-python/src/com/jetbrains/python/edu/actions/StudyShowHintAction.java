package com.jetbrains.python.edu.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.edu.StudyState;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.TaskWindow;
import com.jetbrains.python.edu.editor.StudyEditor;
import icons.StudyIcons;

import java.io.File;

public class StudyShowHintAction extends DumbAwareAction {
  public static final String ACTION_ID = "ShowHintAction";
  public static final String SHORTCUT = "ctrl pressed 7";

  public StudyShowHintAction() {
    super("Show hint", "Show hint", StudyIcons.ShowHint);
  }

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    StudyState studyState = new StudyState(StudyEditor.getSelectedStudyEditor(project));
    if (!studyState.isValid()) {
      return;
    }
    PsiFile file = PsiManager.getInstance(project).findFile(studyState.getVirtualFile());
    final Editor editor = studyState.getEditor();
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    TaskWindow taskWindow = studyState.getTaskFile().getTaskWindow(editor.getDocument(), pos);
    if (file == null || taskWindow == null) {
      return;
    }
    String hint = taskWindow.getHint();
    if (hint == null) {
      return;
    }
    File resourceFile = new File(course.getResourcePath());
    File resourceRoot = resourceFile.getParentFile();
    if (resourceRoot == null || !resourceRoot.exists()) {
      return;
    }
    File hintsDir = new File(resourceRoot, Course.HINTS_DIR);
    if (hintsDir.exists()) {
      String hintText = StudyUtils.getFileText(hintsDir.getAbsolutePath(), hint, true);
      int offset = editor.getDocument().getLineStartOffset(pos.line) + pos.column;
      PsiElement element = file.findElementAt(offset);
      if (hintText == null || element == null) {
        return;
      }

      DocumentationManager documentationManager = DocumentationManager.getInstance(project);
      DocumentationComponent component = new DocumentationComponent(documentationManager);
      component.setData(element, hintText, true, null);
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
  }

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
