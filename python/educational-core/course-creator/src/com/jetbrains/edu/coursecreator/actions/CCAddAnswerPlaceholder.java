package com.jetbrains.edu.coursecreator.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.ui.JBColor;
import com.intellij.util.DocumentUtil;
import com.jetbrains.edu.coursecreator.ui.CCCreateAnswerPlaceholderDialog;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.core.EduAnswerPlaceholderPainter;
import com.jetbrains.edu.learning.core.EduNames;
import com.jetbrains.edu.learning.core.EduUtils;
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder;
import com.jetbrains.edu.learning.courseFormat.TaskFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CCAddAnswerPlaceholder extends CCAnswerPlaceholderAction {

  public CCAddAnswerPlaceholder() {
    super("Add/Delete Answer Placeholder", "Add/Delete answer placeholder", null);
  }


  private static boolean arePlaceholdersIntersect(@NotNull final TaskFile taskFile, @NotNull final Document document, int start, int end) {
    List<AnswerPlaceholder> answerPlaceholders = taskFile.getAnswerPlaceholders();
    for (AnswerPlaceholder existingAnswerPlaceholder : answerPlaceholders) {
      int twStart = existingAnswerPlaceholder.getRealStartOffset(document);
      int twEnd = existingAnswerPlaceholder.getPossibleAnswerLength() + twStart;
      if ((start >= twStart && start < twEnd) || (end > twStart && end <= twEnd) ||
          (twStart >= start && twStart < end) || (twEnd > start && twEnd <= end)) {
        return true;
      }
    }
    return false;
  }

  private static void addPlaceholder(@NotNull CCState state) {
    Editor editor = state.getEditor();
    Project project = state.getProject();
    PsiFile file = state.getFile();

    final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
    if (document == null) {
      return;
    }

    final SelectionModel model = editor.getSelectionModel();
    final int start = model.getSelectionStart();
    final int lineNumber = document.getLineNumber(start);
    int realStart = start - document.getLineStartOffset(lineNumber);
    final AnswerPlaceholder answerPlaceholder = new AnswerPlaceholder();
    answerPlaceholder.setLine(lineNumber);
    answerPlaceholder.setStart(realStart);
    answerPlaceholder.setUseLength(false);
    String selectedText = model.getSelectedText();
    answerPlaceholder.setPossibleAnswer(selectedText);

    CCCreateAnswerPlaceholderDialog dlg = new CCCreateAnswerPlaceholderDialog(project, answerPlaceholder);
    dlg.show();
    if (dlg.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
      return;
    }

    TaskFile taskFile = state.getTaskFile();
    int index = taskFile.getAnswerPlaceholders().size() + 1;
    answerPlaceholder.setIndex(index);
    taskFile.addAnswerPlaceholder(answerPlaceholder);
    answerPlaceholder.setTaskFile(taskFile);
    taskFile.sortAnswerPlaceholders();


    computeInitialState(project, file, taskFile, document);

    EduAnswerPlaceholderPainter.drawAnswerPlaceholder(editor, answerPlaceholder, JBColor.BLUE);
    EduAnswerPlaceholderPainter.createGuardedBlocks(editor, answerPlaceholder);
  }

  private static void computeInitialState(Project project, PsiFile file, TaskFile taskFile, Document document) {
    Document patternDocument = StudyUtils.getPatternDocument(taskFile, file.getName());
    if (patternDocument == null) {
      return;
    }
    DocumentUtil.writeInRunUndoTransparentAction(() -> {
      patternDocument.replaceString(0, patternDocument.getTextLength(), document.getCharsSequence());
      FileDocumentManager.getInstance().saveDocument(patternDocument);
    });
    TaskFile target = new TaskFile();
    TaskFile.copy(taskFile, target);
    List<AnswerPlaceholder> placeholders = target.getAnswerPlaceholders();
    for (AnswerPlaceholder placeholder : placeholders) {
      placeholder.setUseLength(false);
    }
    EduUtils.createStudentDocument(project, target, file.getVirtualFile(), patternDocument);

    for (int i = 0; i < placeholders.size(); i++) {
      AnswerPlaceholder fromPlaceholder = placeholders.get(i);
      taskFile.getAnswerPlaceholders().get(i).setInitialState(fromPlaceholder);
    }
  }

  @Override
  protected void performAnswerPlaceholderAction(@NotNull CCState state) {
    if (canAddPlaceholder(state)) {
      addPlaceholder(state);
      return;
    }
    if (canDeletePlaceholder(state)) {
      deletePlaceholder(state);
    }
  }

  private static void deletePlaceholder(@NotNull CCState state) {
    Project project = state.getProject();
    PsiFile psiFile = state.getFile();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document == null) return;
    TaskFile taskFile = state.getTaskFile();
    AnswerPlaceholder answerPlaceholder = state.getAnswerPlaceholder();
    final List<AnswerPlaceholder> answerPlaceholders = taskFile.getAnswerPlaceholders();
    if (answerPlaceholders.contains(answerPlaceholder)) {
      answerPlaceholders.remove(answerPlaceholder);
      final Editor editor = state.getEditor();
      editor.getMarkupModel().removeAllHighlighters();
      StudyUtils.drawAllWindows(editor, taskFile);
      EduAnswerPlaceholderPainter.createGuardedBlocks(editor, taskFile);
    }
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
    if (canAddPlaceholder(state) || canDeletePlaceholder(state)) {
      presentation.setEnabled(true);
      presentation.setText((state.getAnswerPlaceholder() == null ? "Add " : "Delete ") + EduNames.PLACEHOLDER);
    }
  }


  private static boolean canAddPlaceholder(@NotNull CCState state) {
    Editor editor = state.getEditor();
    SelectionModel selectionModel = editor.getSelectionModel();
    if (!selectionModel.hasSelection()) {
      return false;
    }
    int start = selectionModel.getSelectionStart();
    int end = selectionModel.getSelectionEnd();
    return !arePlaceholdersIntersect(state.getTaskFile(), editor.getDocument(), start, end);
  }

  private static boolean canDeletePlaceholder(@NotNull CCState state) {
    if (state.getEditor().getSelectionModel().hasSelection()) {
      return false;
    }
    return state.getAnswerPlaceholder() != null;
  }
}