package com.jetbrains.edu.coursecreator.actions.placeholder;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UnexpectedUndoException;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.DocumentUtil;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderSubtaskInfo;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import com.jetbrains.edu.learning.courseFormat.tasks.Task;
import com.jetbrains.edu.learning.courseFormat.tasks.TaskWithSubtasks;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCAddAnswerPlaceholder extends CCAnswerPlaceholderAction {

  public CCAddAnswerPlaceholder() {
    super("Add Answer Placeholder", "Add/Delete answer placeholder");
  }


  private static boolean arePlaceholdersIntersect(@NotNull final TaskFile taskFile, int start, int end) {
    List<AnswerPlaceholder> answerPlaceholders = taskFile.getActivePlaceholders();
    for (AnswerPlaceholder existingAnswerPlaceholder : answerPlaceholders) {
      int twStart = existingAnswerPlaceholder.getOffset();
      int twEnd = existingAnswerPlaceholder.getPossibleAnswerLength() + twStart;
      if ((start >= twStart && start < twEnd) || (end > twStart && end <= twEnd) ||
          (twStart >= start && twStart < end) || (twEnd > start && twEnd <= end)) {
        return true;
      }
    }
    return false;
  }

  private void addPlaceholder(@NotNull CCState state) {
    Editor editor = state.getEditor();
    Project project = state.getProject();
    Document document = editor.getDocument();
    FileDocumentManager.getInstance().saveDocument(document);
    final SelectionModel model = editor.getSelectionModel();
    final int offset = model.hasSelection() ? model.getSelectionStart() : editor.getCaretModel().getOffset();
    TaskFile taskFile = state.getTaskFile();
    final Task task = state.getTaskFile().getTask();
    int subtaskIndex = task instanceof TaskWithSubtasks ? ((TaskWithSubtasks)task).getActiveSubtaskIndex() : 0;
    final AnswerPlaceholder answerPlaceholder = new AnswerPlaceholder();
    AnswerPlaceholderSubtaskInfo info = new AnswerPlaceholderSubtaskInfo();
    answerPlaceholder.getSubtaskInfos().put(subtaskIndex, info);
    int index = taskFile.getAnswerPlaceholders().size();
    answerPlaceholder.setIndex(index);
    answerPlaceholder.setTaskFile(taskFile);
    taskFile.sortAnswerPlaceholders();
    answerPlaceholder.setOffset(offset);
    answerPlaceholder.setUseLength(false);

    String defaultPlaceholderText = "type here";
    CCCreateAnswerPlaceholderDialog dlg = createDialog(project, answerPlaceholder);
    if (!dlg.showAndGet()) {
      return;
    }
    String answerPlaceholderText = dlg.getTaskText();
    answerPlaceholder.setPossibleAnswer(model.hasSelection() ? model.getSelectedText() : defaultPlaceholderText);
    answerPlaceholder.setTaskText(StringUtil.notNullize(answerPlaceholderText));
    answerPlaceholder.setLength(StringUtil.notNullize(answerPlaceholderText).length());
    answerPlaceholder.setHints(dlg.getHints());

    if (!model.hasSelection()) {
      DocumentUtil.writeInRunUndoTransparentAction(() -> document.insertString(offset, defaultPlaceholderText));
    }

    answerPlaceholder.setPossibleAnswer(model.hasSelection() ? model.getSelectedText() : defaultPlaceholderText);
    AddAction action = new AddAction(answerPlaceholder, taskFile, editor);
    EduUtils.runUndoableAction(project, "Add Answer Placeholder", action);
  }

  static class AddAction extends BasicUndoableAction {
    private final AnswerPlaceholder myPlaceholder;
    private final TaskFile myTaskFile;
    private final Editor myEditor;

    public AddAction(AnswerPlaceholder placeholder, TaskFile taskFile, Editor editor) {
      super(editor.getDocument());
      myPlaceholder = placeholder;
      myTaskFile = taskFile;
      myEditor = editor;
    }

    @Override
    public void undo() throws UnexpectedUndoException {
      final List<AnswerPlaceholder> answerPlaceholders = myTaskFile.getAnswerPlaceholders();
      if (answerPlaceholders.contains(myPlaceholder)) {
        answerPlaceholders.remove(myPlaceholder);
        myEditor.getMarkupModel().removeAllHighlighters();
        StudyUtils.drawAllAnswerPlaceholders(myEditor, myTaskFile);
        EduAnswerPlaceholderPainter.createGuardedBlocks(myEditor, myTaskFile);
      }
    }

    @Override
    public void redo() throws UnexpectedUndoException {
      myTaskFile.addAnswerPlaceholder(myPlaceholder);
      EduAnswerPlaceholderPainter.drawAnswerPlaceholder(myEditor, myPlaceholder, JBColor.BLUE);
      EduAnswerPlaceholderPainter.createGuardedBlocks(myEditor, myPlaceholder);
    }
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    addPlaceholder(state);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    presentation.setEnabledAndVisible(false);

    CCState state = getState(event);
    if (state == null) {
      return;
    }

    presentation.setVisible(true);
    if (canAddPlaceholder(state)) {
      presentation.setEnabled(true);
    }
  }


  private static boolean canAddPlaceholder(@NotNull CCState state) {
    Editor editor = state.getEditor();
    SelectionModel selectionModel = editor.getSelectionModel();
    TaskFile taskFile = state.getTaskFile();
    if (selectionModel.hasSelection()) {
      int start = selectionModel.getSelectionStart();
      int end = selectionModel.getSelectionEnd();
      return !arePlaceholdersIntersect(taskFile, start, end);
    }
    int offset = editor.getCaretModel().getOffset();
    return StudyUtils.getAnswerPlaceholder(offset, taskFile.getAnswerPlaceholders()) == null;
  }

  protected CCCreateAnswerPlaceholderDialog createDialog(Project project, AnswerPlaceholder answerPlaceholder) {
    String answerPlaceholderText = StringUtil.notNullize(answerPlaceholder.getTaskText());
    return new CCCreateAnswerPlaceholderDialog(project, answerPlaceholderText.isEmpty() ? "type here" : answerPlaceholderText,
                                               answerPlaceholder.getHints());
  }
}