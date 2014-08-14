package com.jetbrains.python.edu.actions;

import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.edu.StudyTaskManager;
import com.jetbrains.python.edu.StudyUtils;
import com.jetbrains.python.edu.course.Course;
import com.jetbrains.python.edu.course.TaskFile;
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
    Project project = e.getProject();
    if (project != null) {
      DocumentationManager documentationManager = DocumentationManager.getInstance(project);
      DocumentationComponent component = new DocumentationComponent(documentationManager);
      Editor selectedEditor = StudyEditor.getSelectedEditor(project);
      FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
      assert selectedEditor != null;
      VirtualFile openedFile = fileDocumentManager.getFile(selectedEditor.getDocument());
      if (openedFile != null) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(e.getProject());
        TaskFile taskFile = taskManager.getTaskFile(openedFile);
        if (taskFile != null) {
          PsiFile file = PsiManager.getInstance(project).findFile(openedFile);
          if (file != null) {
            LogicalPosition pos = selectedEditor.getCaretModel().getLogicalPosition();
            TaskWindow taskWindow = taskFile.getTaskWindow(selectedEditor.getDocument(), pos);
            if (taskWindow != null) {
              String hint = taskWindow.getHint();
              if (hint == null) {
                return;
              }
              Course course = taskManager.getCourse();
              if (course != null) {
                File resourceFile = new File(course.getResourcePath());
                File resourceRoot = resourceFile.getParentFile();
                if (resourceRoot != null && resourceRoot.exists()) {
                  File hintsDir = new File(resourceRoot, Course.HINTS_DIR);
                  if (hintsDir.exists()) {
                    String hintText = StudyUtils.getFileText(hintsDir.getAbsolutePath(), hint, true);
                    if (hintText != null) {
                      int offset = selectedEditor.getDocument().getLineStartOffset(pos.line) + pos.column;
                      PsiElement element = file.findElementAt(offset);
                      if (element != null) {
                        component.setData(element, hintText, true, null);
                        final JBPopup popup =
                          JBPopupFactory.getInstance().createComponentPopupBuilder(component, component)
                            .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false)
                            .setResizable(true)
                            .setMovable(true)
                            .setRequestFocus(true)
                            .createPopup();
                        component.setHint(popup);
                        popup.showInBestPositionFor(selectedEditor);
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  @Override
  public void update(AnActionEvent e) {
    StudyUtils.updateAction(e);
  }
}
