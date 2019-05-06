package com.intellij.sh.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;
import com.intellij.sh.highlighting.ShTextOccurrencesUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.editor.actions.IncrementalFindAction.SEARCH_DISABLED;

public class ShSelectAllOccurrencesHandler extends EditorActionHandler {
  public static final ShSelectAllOccurrencesHandler INSTANCE = new ShSelectAllOccurrencesHandler();
  private static final Logger LOG = Logger.getInstance(ShSelectAllOccurrencesHandler.class);

  @Override
  public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    return editor.getProject() != null
        && editor.getCaretModel().supportsMultipleCarets()
        && !SEARCH_DISABLED.get(editor, false);
  }

  @Override
  public void doExecute(@NotNull final Editor editor, @Nullable Caret c, DataContext dataContext) {
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    int caretOffset = caret.getOffset();
    TextRange caretTextRange;
    boolean matchExactWords;
    if (caret.hasSelection()) {
      SelectionModel selectionModel = editor.getSelectionModel();
      caretTextRange = new TextRange(selectionModel.getSelectionStart(), selectionModel.getSelectionEnd());
      matchExactWords = false;
    }
    else {
      caretTextRange = ShTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(editor);
      if (caretTextRange == null) return;
      matchExactWords = true;
    }
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    CharSequence textToFind = caretTextRange.subSequence(documentText);
    List<TextRange> occurrences = ShTextOccurrencesUtil.findAllOccurrences(documentText, textToFind, matchExactWords);
    if (!occurrences.remove(caretTextRange)) {
      LOG.error("Cannot find " + caretTextRange + " in all occurrences");
    }
    occurrences.add(0, caretTextRange); // workaround to restore the original caret on Escape

    List<CaretState> caretStates = ContainerUtil.newSmartList();
    for (TextRange occurrence : occurrences) {
      int caretInSelection = occurrence.getStartOffset() + caretOffset - caretTextRange.getStartOffset();
      CaretState caretState = new CaretState(editor.offsetToLogicalPosition(caretInSelection),
          editor.offsetToLogicalPosition(occurrence.getStartOffset()),
          editor.offsetToLogicalPosition(occurrence.getEndOffset()));
      caretStates.add(caretState);
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }
}
