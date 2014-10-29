package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.Course;
import org.jetbrains.plugins.coursecreator.format.TaskFile;
import org.jetbrains.plugins.coursecreator.format.TaskWindow;

import java.util.List;

public class CCDeleteTaskWindow extends CCTaskWindowAction {
  private static final Logger LOG = Logger.getInstance(CCDeleteTaskWindow.class);

  public CCDeleteTaskWindow() {
    super("Delete Answer Placeholder","Delete answer placeholder", null);
  }

  @Override
  protected void performTaskWindowAction(@NotNull CCState state) {
    Project project = state.getProject();
    PsiFile psiFile = state.getFile();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    TaskFile taskFile = state.getTaskFile();
    TaskWindow taskWindow = state.getTaskWindow();
    final List<TaskWindow> taskWindows = taskFile.getTaskWindows();
    if (taskWindows.contains(taskWindow)) {
      taskWindow.removeResources(project);
      taskWindows.remove(taskWindow);
      final Editor editor = state.getEditor();
      editor.getMarkupModel().removeAllHighlighters();
      CCProjectService.getInstance(project).drawTaskWindows(psiFile.getVirtualFile(), editor, course);
      taskFile.createGuardedBlocks(editor);
    }
  }
}