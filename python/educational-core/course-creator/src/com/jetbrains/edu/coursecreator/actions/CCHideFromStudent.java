package com.jetbrains.edu.coursecreator.actions;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.Course;
import com.jetbrains.edu.learning.courseFormat.Task;
import com.jetbrains.edu.learning.courseFormat.TaskFile;

import java.io.IOException;
import java.util.Map;

public class CCHideFromStudent extends CCTaskFileActionBase {

  private static final Logger LOG = Logger.getInstance(CCHideFromStudent.class);
  private static final String ACTION_NAME = "Hide from Student";

  public CCHideFromStudent() {
    super(ACTION_NAME);
  }

  @Override
  protected void performAction(VirtualFile file, Task task, Course course, Project project) {
    TaskFile taskFile = StudyUtils.getTaskFile(project, file);
    if (taskFile == null) {
      return;
    }
    EduUtils.runUndoableAction(project, ACTION_NAME, new HideTaskFile(project, course, file, task, taskFile));
  }

  private static class HideTaskFile extends BasicUndoableAction {

    private final Project myProject;
    private final Course myCourse;
    private final VirtualFile myFile;
    private final Task myTask;
    private final TaskFile myTaskFile;

    public HideTaskFile(Project project, Course course, VirtualFile file, Task task, TaskFile taskFile) {
      super(file);
      myProject = project;
      myCourse = course;
      myFile = file;
      myTask = task;
      myTaskFile = taskFile;
    }

    @Override
    public void undo() throws UnexpectedUndoException {
      myTask.getTaskFiles().put(myFile.getName(), myTaskFile);
      CCUtils.createResourceFile(myFile, myCourse, StudyUtils.getTaskDir(myFile));
      if (!myTaskFile.getAnswerPlaceholders().isEmpty() && FileEditorManager.getInstance(myProject).isFileOpen(myFile)) {
        for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getEditors(myFile)) {
          if (fileEditor instanceof TextEditor) {
            Editor editor = ((TextEditor)fileEditor).getEditor();
            StudyUtils.drawAllWindows(editor, myTaskFile);
          }
        }
      }
      ProjectView.getInstance(myProject).refresh();
    }

    @Override
    public void redo() throws UnexpectedUndoException {
      hideFromStudent(myFile, myProject, myTask.getTaskFiles(), myTaskFile);
      ProjectView.getInstance(myProject).refresh();
    }

    @Override
    public boolean isGlobal() {
      return true;
    }
  }

  public static void hideFromStudent(VirtualFile file, Project project, Map<String, TaskFile> taskFiles, TaskFile taskFile) {
    if (!taskFile.getAnswerPlaceholders().isEmpty() && FileEditorManager.getInstance(project).isFileOpen(file)) {
      for (FileEditor fileEditor : FileEditorManager.getInstance(project).getEditors(file)) {
        if (fileEditor instanceof TextEditor) {
          Editor editor = ((TextEditor)fileEditor).getEditor();
          editor.getMarkupModel().removeAllHighlighters();
        }
      }
    }
    String name = file.getName();
    VirtualFile patternFile = StudyUtils.getPatternFile(taskFile, name);
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (patternFile != null) {
        try {
          patternFile.delete(CCHideFromStudent.class);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }
    });
    taskFiles.remove(name);
  }

  @Override
  protected boolean isAvailable(Project project, VirtualFile file) {
    return StudyUtils.getTaskFile(project, file) != null;
  }
}
