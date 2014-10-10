package org.jetbrains.plugins.coursecreator;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ReadOnlyFragmentModificationException;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.actionSystem.ReadonlyFragmentModificationHandler;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.*;

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
    Course course = CCProjectService.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    final VirtualFile taskDir = virtualFile.getParent();
    if (taskDir == null || !taskDir.getName().contains("task")) {
      return;
    }
    final VirtualFile lessonDir = taskDir.getParent();
    if (lessonDir == null) return;
    final Lesson lesson = course.getLesson(lessonDir.getName());
    final Task task = lesson.getTask(taskDir.getName());
    final TaskFile taskFile = task.getTaskFile(virtualFile.getName());
    if (taskFile == null) {
      return;
    }
    TaskFileModificationListener listener = new TaskFileModificationListener(taskFile);
    CCProjectService.addDocumentListener(editor.getDocument(), listener);
    editor.getDocument().addDocumentListener(listener);
    EditorActionManager.getInstance()
      .setReadonlyFragmentModificationHandler(editor.getDocument(), new TaskWindowDeleteHandler(editor));
    CCProjectService.drawTaskWindows(virtualFile, editor, course);
    editor.getColorsScheme().setColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR, null);
    taskFile.createGuardedBlocks(editor);
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
    protected void updateTaskWindowLength(CharSequence fragment, TaskWindow taskWindow, int change) {
        int newLength = taskWindow.getReplacementLength() + change;
        taskWindow.setReplacementLength(newLength <= 0 ? 0 : newLength);
    }

    @Override
    protected boolean useLength() {
      return false;
    }
  }

  private static class TaskWindowDeleteHandler implements ReadonlyFragmentModificationHandler {

    private final Editor myEditor;

    public TaskWindowDeleteHandler(@NotNull final Editor editor) {
      myEditor = editor;
    }

    @Override
    public void handle(ReadOnlyFragmentModificationException e) {
      HintManager.getInstance().showErrorHint(myEditor, "Delete task window before editing its borders");
    }
  }
}
