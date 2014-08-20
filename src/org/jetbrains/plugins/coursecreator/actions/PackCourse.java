package org.jetbrains.plugins.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.plugins.coursecreator.CCProjectService;
import org.jetbrains.plugins.coursecreator.format.*;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

public class PackCourse extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(PackCourse.class.getName());
  public PackCourse() {
    super("Generate course archive","Generate course archive", null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return;
    }

    final CCProjectService service = CCProjectService.getInstance(project);
    final Course course = service.getCourse();
    if (course == null) return;
    final VirtualFile baseDir = project.getBaseDir();
    final Map<String, Lesson> lessons = course.getLessonsMap();

    List<FileEditor> editorList = new ArrayList<FileEditor>();

    for (Map.Entry<String, Lesson> lesson : lessons.entrySet()) {
      final VirtualFile lessonDir = baseDir.findChild(lesson.getKey());
      if (lessonDir == null) continue;
      for (Map.Entry<String, Task> task : lesson.getValue().myTasksMap.entrySet()) {
        final VirtualFile taskDir = lessonDir.findChild(task.getKey());
        if (taskDir == null) continue;
        for (Map.Entry<String, TaskFile> entry : task.getValue().task_files.entrySet()) {
          final VirtualFile file = taskDir.findChild(entry.getKey());
          if (file == null) continue;
          final Document document = FileDocumentManager.getInstance().getDocument(file);
          if (document == null) continue;

          final TaskFile taskFile = entry.getValue();
          for (final TaskWindow taskWindow : taskFile.getTaskWindows()) {
            final String taskText = taskWindow.getTaskText();
            final int lineStartOffset = document.getLineStartOffset(taskWindow.line);
            final int offset = lineStartOffset + taskWindow.start;
            final FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
            if (editors.length > 0)
              editorList.add(editors[0]);

            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              @Override
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  @Override
                  public void run() {
                    document.replaceString(offset, offset + taskWindow.getReplacementLength(), taskText);
                    FileDocumentManager.getInstance().saveDocument(document);
                  }
                });
              }
            }, "x", "qwe");


          }
        }
      }
    }
    try {
      File zipFile = new File(project.getBasePath(), course.getName() + ".zip");
      ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)));

      for (Map.Entry<String, Lesson> entry : lessons.entrySet()) {
        final VirtualFile lessonDir = baseDir.findChild(entry.getKey());
        if (lessonDir == null) continue;

        ZipUtil.addFileOrDirRecursively(zos, null, new File(lessonDir.getPath()), lessonDir.getName(), null, null);
      }
      ZipUtil.addFileOrDirRecursively(zos, null, new File(baseDir.getPath(), "hints"), "hints", null, null);
      ZipUtil.addFileOrDirRecursively(zos, null, new File(baseDir.getPath(), "course.json"), "course.json", null, null);
      ZipUtil.addFileOrDirRecursively(zos, null, new File(baseDir.getPath(), "test_helper.py"), "test_helper.py", null, null);
      zos.close();
    } catch (IOException e1) {
      LOG.error(e1);
    }

    for (FileEditor fileEditor : editorList) {
      UndoManager.getInstance(project).undo(fileEditor);
    }
  }

}