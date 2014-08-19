package org.jetbrains.plugins.coursecreator;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.format.*;
import org.jetbrains.plugins.coursecreator.highlighting.TaskTextGutter;

import java.util.Map;

public class CCTaskWindowsUpdater implements StartupActivity {
  private static final Logger LOG = Logger.getInstance(CCTaskWindowsUpdater.class.getName());

  @Override
  public void runActivity(@NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }
    final Course course = CCProjectService.getInstance(project).getCourse();
    final VirtualFile baseDir = project.getBaseDir();
    if (course != null) {
      final Map<String, Lesson> lessonsMap = course.getLessonsMap();
      for (Map.Entry<String, Lesson> nameLessonEntry : lessonsMap.entrySet()) {
        final VirtualFile lessonDir = baseDir.findChild(nameLessonEntry.getKey());
        if (lessonDir == null) continue;
        final Map<String, Task> tasksMap = nameLessonEntry.getValue().myTasksMap;
        for (Map.Entry<String, Task> nameTaskEntry : tasksMap.entrySet()) {
          final VirtualFile taskDir = lessonDir.findChild(nameTaskEntry.getKey());
          if (taskDir == null) continue;
          final Map<String, TaskFile> taskFiles = nameTaskEntry.getValue().taskFiles;
          for (Map.Entry<String, TaskFile> nameTaskFileEntry : taskFiles.entrySet()) {
            final VirtualFile taskFile = taskDir.findChild(nameTaskFileEntry.getKey());
            if (taskFile == null) continue;

            final FileEditor fileEditor = FileEditorManager.getInstance(project).getEditors(taskFile)[0];
            if (fileEditor instanceof TextEditor) {
              final Editor editor = ((TextEditor)fileEditor).getEditor();
              final TaskFile value = nameTaskFileEntry.getValue();
              for (TaskWindow taskWindow : value.getTaskWindows()) {
                final RangeHighlighter rangeHighlighter = editor.getMarkupModel().addLineHighlighter(taskWindow.line, HighlighterLayer.FIRST, TextAttributes.ERASE_MARKER);
                rangeHighlighter.setGutterIconRenderer(new TaskTextGutter(taskWindow));
              }

            }
          }
        }
      }
    }
  }
}
