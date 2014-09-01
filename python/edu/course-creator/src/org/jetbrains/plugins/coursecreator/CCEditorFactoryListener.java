package org.jetbrains.plugins.coursecreator;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
    CCProjectService.drawTaskWindows(virtualFile, editor, course);
  }

  @Override
  public void editorReleased(@NotNull EditorFactoryEvent event) {
    Editor editor = event.getEditor();
    Document document = editor.getDocument();
    StudyDocumentListener listener = CCProjectService.getListener(document);
    if (listener != null) {
      document.removeDocumentListener(listener);
      CCProjectService.removeListener(document);
    }
    editor.getMarkupModel().removeAllHighlighters();
    editor.getSelectionModel().removeSelection();
  }

  private static class TaskFileModificationListener extends StudyDocumentListener {

    public TaskFileModificationListener(TaskFile taskFile) {
      super(taskFile);
    }

    @Override
    protected void updateTaskWindowLength(CharSequence fragment, TaskWindow taskWindow, int change) {
        int newLength = taskWindow.getReplacementLength() + change;
        taskWindow.setReplacementLength(newLength <= 0 ? 0 : newLength);
        if (fragment.equals("\n")) {
          taskWindow.setReplacementLength(taskWindow.getLength() + 1);
        }
    }
  }
}
