package com.intellij.sh.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.sh.highlighting.ShTextOccurrencesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.editor.actions.IncrementalFindAction.SEARCH_DISABLED;

public class ShRenameAllOccurrencesHandler extends EditorActionHandler {
  public static final ShRenameAllOccurrencesHandler INSTANCE = new ShRenameAllOccurrencesHandler();

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return editor.getProject() != null
        && editor.getCaretModel().supportsMultipleCarets()
        && !SEARCH_DISABLED.get(editor, false);
  }

  @Override
  public void doExecute(@NotNull final Editor editor, @Nullable Caret c, DataContext dataContext) {
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    TextRange caretTextRange;
    boolean matchExactWords = true;
    if (caret.hasSelection()) {
      matchExactWords = false;
      SelectionModel selectionModel = editor.getSelectionModel();
      caretTextRange = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
    }
    else {
      caretTextRange = ShTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(editor);
      if (caretTextRange == null) return;
    }
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    CharSequence textToFind = caretTextRange.subSequence(documentText);
    List<TextRange> occurrences = ShTextOccurrencesUtil.findAllOccurrences(documentText, textToFind, matchExactWords);
    Project project = Objects.requireNonNull(editor.getProject());
    BashTextRenameRefactoring rename = BashTextRenameRefactoring.create(editor, project, textToFind.toString(), occurrences, caretTextRange);
    if (rename != null) {
      rename.start();
    }
  }
}
