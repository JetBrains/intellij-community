package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.CCUtils;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CCDeleteAllAnswerPlaceholdersAction extends DumbAwareAction {

  public static final String ACTION_NAME = "Delete All " + EduNames.ANSWER_PLACEHOLDER + "s";

  public CCDeleteAllAnswerPlaceholdersAction() {
    super(ACTION_NAME);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(context);
    final Project project = e.getProject();
    if (file == null || project == null) {
      return;
    }
    final TaskFile taskFile = StudyUtils.getTaskFile(project, file);
    if (taskFile == null) {
      return;
    }
    Editor editor = CommonDataKeys.EDITOR.getData(context);
    if (editor == null) {
      FileEditorManager instance = FileEditorManager.getInstance(project);
      if (!instance.isFileOpen(file)) {
        return;
      }
      FileEditor fileEditor = instance.getSelectedEditor(file);
      if (!(fileEditor instanceof TextEditor)) {
        return;
      }
      editor = ((TextEditor)fileEditor).getEditor();
    }
    List<AnswerPlaceholder> placeholders = new ArrayList<>(taskFile.getAnswerPlaceholders());
    final ClearPlaceholders action = new ClearPlaceholders(taskFile, placeholders, editor);
    EduUtils.runUndoableAction(project, ACTION_NAME, action, UndoConfirmationPolicy.REQUEST_CONFIRMATION);
  }

  private static void updateView(@NotNull final Editor editor,
                                 @NotNull final TaskFile taskFile) {
    editor.getMarkupModel().removeAllHighlighters();
    StudyUtils.drawAllWindows(editor, taskFile);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(false);

    Project project = e.getProject();
    if (project == null) {
      return;
    }
    if (!CCUtils.isCourseCreator(project)) {
      return;
    }
    DataContext context = e.getDataContext();
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(context);
    if (file == null ) {
      return;
    }
    TaskFile taskFile = StudyUtils.getTaskFile(project, file);
    if (taskFile == null || taskFile.getAnswerPlaceholders().isEmpty()) {
      return;
    }
    presentation.setEnabledAndVisible(true);
  }


  private static class ClearPlaceholders extends BasicUndoableAction {
    private final List<AnswerPlaceholder> myPlaceholders;
    private final Editor myEditor;
    private final TaskFile myTaskFile;

    public ClearPlaceholders(TaskFile taskFile, List<AnswerPlaceholder> placeholders, Editor editor) {
      super(editor.getDocument());
      myTaskFile = taskFile;
      myPlaceholders = placeholders;
      myEditor = editor;
    }

    @Override
    public void undo() throws UnexpectedUndoException {
      myTaskFile.getAnswerPlaceholders().addAll(myPlaceholders);
      updateView(myEditor, myTaskFile);
    }

    @Override
    public void redo() throws UnexpectedUndoException {
      myTaskFile.getAnswerPlaceholders().clear();
      updateView(myEditor, myTaskFile);
    }

    @Override
    public boolean isGlobal() {
      return true;
    }
  }
}
