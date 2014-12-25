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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class StudyShowHintAction extends DumbAwareAction {
  public static final String ACTION_ID = "ShowHintAction";
  public static final String SHORTCUT = "ctrl pressed 7";
  public static final String OUTSIDE_TASK_WINDOW_MESSAGE = "Put the caret in the answer placeholder to get hint";
  public static final String HINT_NOT_AVAILABLE = "There is no hint for this answer placeholder";

  public StudyShowHintAction() {
    super("Show hint", "Show hint", StudyIcons.ShowHint);
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
    StudyState studyState = new StudyState(StudyEditor.getSelectedStudyEditor(project));
    if (!studyState.isValid()) {
      return;
    }
    PsiFile file = PsiManager.getInstance(project).findFile(studyState.getVirtualFile());
    final Editor editor = studyState.getEditor();
    LogicalPosition pos = editor.getCaretModel().getLogicalPosition();
    TaskWindow taskWindow = studyState.getTaskFile().getTaskWindow(editor.getDocument(), pos);
    if (file == null) {
      return;
    }
    String hintText = OUTSIDE_TASK_WINDOW_MESSAGE;
    if (taskWindow != null) {
      hintText = getHintText(taskWindow, course);
    }
    int offset = editor.getDocument().getLineStartOffset(pos.line) + pos.column;
    PsiElement element = file.findElementAt(offset);
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    DocumentationComponent component = new DocumentationComponent(documentationManager);
    component.setData(element != null ? element : file, element != null ? hintText : OUTSIDE_TASK_WINDOW_MESSAGE, true, null);
    showHintPopUp(project, editor, component);
  }

  @Nullable
  private static String getHintText(@NotNull final TaskWindow taskWindow, @NotNull final Course course) {
    String hintFileName = taskWindow.getHint();
    String hintText = HINT_NOT_AVAILABLE;
    if (hintFileName != null && !hintFileName.isEmpty()) {
      File resourceFile = new File(course.getResourcePath());
      File resourceRoot = resourceFile.getParentFile();
      if (resourceRoot != null && resourceRoot.exists()) {
        File hintsDir = new File(resourceRoot, Course.HINTS_DIR);
        if (hintsDir.exists()) {
          hintText = StudyUtils.getFileText(hintsDir.getAbsolutePath(), hintFileName, true, "UTF-8");
        }
      }
    }
    return  hintText != null ? hintText : OUTSIDE_TASK_WINDOW_MESSAGE;
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
