package com.jetbrains.edu.coursecreator;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.edu.learning.*;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCSubtaskChangeListener implements StudySubtaskChangeListener {
  @Override
  public void subtaskChanged(@NotNull Project project, @NotNull Task task, int oldSubtaskNumber, int newSubtaskNumber) {
    VirtualFile taskDir = task.getTaskDir(project);
    if (taskDir == null) {
      return;
    }
    Course course = StudyTaskManager.getInstance(project).getCourse();
    if (course == null) {
      return;
    }
    EduPluginConfigurator configurator = EduPluginConfigurator.INSTANCE.forLanguage(course.getLanguageById());
    if (configurator == null) {
      return;
    }
    String testFileName = configurator.getTestFileName();
    if (newSubtaskNumber != 0) {
      String nameWithoutExtension = FileUtil.getNameWithoutExtension(testFileName);
      String extension = FileUtilRt.getExtension(testFileName);
      testFileName = nameWithoutExtension + EduNames.SUBTASK_MARKER + newSubtaskNumber + "." + extension;
    }
    VirtualFile newTestFile = taskDir.findChild(testFileName);
    if (newTestFile == null) {
      return;
    }
    FileEditorManager editorManager = FileEditorManager.getInstance(project);
    List<VirtualFile> testFiles =
      ContainerUtil.filter(taskDir.getChildren(), file -> CCUtils.isTestsFile(project, file) && editorManager.isFileOpen(file));
    if (testFiles.isEmpty()) {
      return;
    }
    Editor selectedTextEditor = editorManager.getSelectedTextEditor();
    for (VirtualFile testFile : testFiles) {
      editorManager.closeFile(testFile);
    }
    editorManager.openFile(newTestFile, true);
    if (selectedTextEditor != null) {
      reopenSelectedEditor(editorManager, selectedTextEditor);
    }
  }

  private static void reopenSelectedEditor(FileEditorManager editorManager, Editor selectedTextEditor) {
    Document document = selectedTextEditor.getDocument();
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !editorManager.isFileOpen(virtualFile)) {
      return;
    }
    editorManager.openFile(virtualFile, true);
  }
}
