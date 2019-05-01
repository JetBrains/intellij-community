package com.intellij.bash.rename;

import com.intellij.bash.highlighting.BashTextOccurrencesUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.editor.actions.IncrementalFindAction.SEARCH_DISABLED;

public class BashSelectAllOccurrencesAction extends EditorAction {

  private static final Logger LOG = Logger.getInstance(BashSelectAllOccurrencesAction.class);
  private static final String ACTION_ID = "BashSelectAllOccurrences";

  protected BashSelectAllOccurrencesAction() {
    super(new Handler());
  }

  static BashSelectAllOccurrencesAction findAction() {
    AnAction action = ActionManager.getInstance().getAction(ACTION_ID);
    if (action == null) {
      LOG.error("Cannot find " + ACTION_ID);
      return new BashSelectAllOccurrencesAction();
    }
    if (action instanceof BashSelectAllOccurrencesAction) {
      return (BashSelectAllOccurrencesAction) action;
    }
    LOG.error("Cannot cast " + action.getClass() + " to " + BashSelectAllOccurrencesAction.class);
    return new BashSelectAllOccurrencesAction();
  }

  private static class Handler extends EditorActionHandler {
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
        caretTextRange = new TextRange(editor.getSelectionModel().getSelectionStart(),
            editor.getSelectionModel().getSelectionEnd());
        matchExactWords = false;
      }
      else {
        caretTextRange = BashTextOccurrencesUtil.findTextRangeOfIdentifierAtCaret(editor);
        matchExactWords = true;
        if (caretTextRange == null) return;
      }
      CharSequence documentText = editor.getDocument().getImmutableCharSequence();
      CharSequence textToFind = caretTextRange.subSequence(documentText);
      List<TextRange> occurrences = BashTextOccurrencesUtil.findAllOccurrences(documentText, textToFind, matchExactWords);
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
}
