package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.EduTaskWindowDeleteHandler;
import com.jetbrains.edu.courseFormat.Course;
import com.jetbrains.edu.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

public class CCEditorFactoryListener implements EditorFactoryListener {
  @Override
  public void editorCreated(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    Project project = editor.getProject();
    if (project == null) {
      return;
    }
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (virtualFile == null) {
      return;
    }
    final CCProjectService service = CCProjectService.getInstance(project);
    Course course = service.getCourse();
    if (course == null) {
      return;
    }
    final VirtualFile taskDir = virtualFile.getParent();
    if (taskDir == null || !taskDir.getName().contains("task")) {
      return;
    }
    final VirtualFile lessonDir = taskDir.getParent();
    if (lessonDir == null) return;
    final TaskFile taskFile = service.getTaskFile(virtualFile);
    if (taskFile == null) {
      return;
    }
    TaskFileModificationListener listener = new TaskFileModificationListener(taskFile);
    CCProjectService.addDocumentListener(editor.getDocument(), listener);
    editor.getDocument().addDocumentListener(listener);
    EditorActionManager.getInstance()
      .setReadonlyFragmentModificationHandler(editor.getDocument(), new EduTaskWindowDeleteHandler(editor));
    service.drawTaskWindows(virtualFile, editor);
    editor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null);
    EduAnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile, false);
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    Document document = editor.getDocument();
    CCDocumentListener listener = CCProjectService.getListener(document);
    if (listener != null) {
      document.removeDocumentListener(listener);
      CCProjectService.removeListener(document);
    }
    editor.getMarkupModel().removeAllHighlighters();
    editor.getSelectionModel().removeSelection();
  }

  private static class TaskFileModificationListener extends CCDocumentListener {

    public TaskFileModificationListener(TaskFile taskFile) {
      super(taskFile);
    }

    @Override
    protected boolean useLength() {
      return false;
    }
  }

}
