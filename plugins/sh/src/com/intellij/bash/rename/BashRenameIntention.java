package com.intellij.bash.rename;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actions.IncrementalFindAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BashRenameIntention extends BaseIntentionAction {
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
    int offset = caret.getOffset();
    TextRange textRange;
    if (caret.hasSelection()) {
      textRange = new TextRange(editor.getSelectionModel().getSelectionStart(),
                                editor.getSelectionModel().getSelectionEnd());
    }
    else {
      textRange = SelectWordUtil.getWordSelectionRange(editor.getDocument().getCharsSequence(),
          caret.getOffset(),
          ch -> "/\\$ \t\r\n\"';".indexOf(ch) == -1
      );
      if (textRange == null) return;
    }
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    CharSequence text = textRange.subSequence(documentText);
    List<Integer> startOffsets = findStartOffsets(documentText, text);
    int ind = startOffsets.indexOf(textRange.getStartOffset());
    assert ind >= 0;
    startOffsets.remove(ind);
    startOffsets.add(0, textRange.getStartOffset());

    List<CaretState> caretStates = new ArrayList<>();
    for (Integer startOffset : startOffsets) {
      int caretOffset = startOffset + offset - textRange.getStartOffset();
      LogicalPosition selectionStart = editor.offsetToLogicalPosition(startOffset);
      LogicalPosition selectionEnd = editor.offsetToLogicalPosition(startOffset + text.length());
      CaretState caretState = new CaretState(editor.offsetToLogicalPosition(caretOffset), selectionStart, selectionEnd);
      caretStates.add(caretState);
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }

  @NotNull
  private static List<Integer> findStartOffsets(@NotNull CharSequence documentText, @NotNull CharSequence textToFind) {
    List<Integer> result = new ArrayList<>();
    int offset = StringUtil.indexOf(documentText, textToFind);
    while (offset >= 0) {
      result.add(offset);
      offset = StringUtil.indexOf(documentText, textToFind, offset + textToFind.length());
    }
    return result;
  }
}
