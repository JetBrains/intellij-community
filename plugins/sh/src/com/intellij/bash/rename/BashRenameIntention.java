package com.intellij.bash.rename;

import com.intellij.bash.highlighting.BashTextOccurrencesUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.IncrementalFindAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BashRenameIntention extends BaseIntentionAction {

  private static final Logger LOG = Logger.getInstance(BashRenameIntention.class);

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @NotNull
  @Override
  public String getText() {
    return "Rename text under caret";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return editor.getProject() != null
        && editor.getCaretModel().supportsMultipleCarets()
        && !IncrementalFindAction.SEARCH_DISABLED.get(editor, false);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    if (carets.size() != 1) return;
    Caret caret = carets.get(0);
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

    List<CaretState> caretStates = new ArrayList<>();
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
