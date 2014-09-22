package com.jetbrains.python.edu;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.python.edu.course.TaskFile;
import com.jetbrains.python.edu.editor.StudyEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class provides editor notifications about task file invalidation
 */
public class StudyEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("taskfile.invalid..editing.notification.panel");
  private final Project myProject;

  public StudyEditorNotificationProvider(@NotNull final Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull final FileEditor fileEditor) {
    final TaskFile taskFile = StudyTaskManager.getInstance(myProject).getTaskFile(file);
    if (taskFile == null) {
      return null;
    }
    if (taskFile.isValid()) {
      return null;
    }
    if (!(fileEditor instanceof StudyEditor)) {
      return null;
    }
    final Editor editor = ((StudyEditor)fileEditor).getEditor();
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText("Task file is invalid\n");
    final InvalidTaskFileFix fix = StudyEditor.getFix(taskFile);
    if (fix != null) {
      panel.createActionLabel("fix file", new Runnable() {
        @Override
        public void run() {
          final Document document = editor.getDocument();
          StudyEditor.deleteGuardedBlocks(document);
          editor.getMarkupModel().removeAllHighlighters();
          taskFile.setTrackChanges(false);
          fix.applyFix(document);
          taskFile.setTrackChanges(true);
          taskFile.drawAllWindows(editor);
          StudyEditor.deleteFix(taskFile);
          taskFile.setValidAndUpdate(true, file, fix.getProject());
        }
      });
    }
    else {
      panel.createActionLabel("refresh task file", "RefreshTaskAction");
    }
    return panel;
  }
}
