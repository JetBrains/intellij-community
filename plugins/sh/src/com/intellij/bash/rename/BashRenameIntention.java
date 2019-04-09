package com.intellij.bash.rename;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
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
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    if (carets.size() != 1) return;
    int offset = carets.get(0).getOffset();
    TextRange selectionRange = new TextRange(editor.getSelectionModel().getSelectionStart(),
        editor.getSelectionModel().getSelectionEnd());
    TextRange textRange = selectionRange;
    CharSequence documentText = editor.getDocument().getImmutableCharSequence();
    if (textRange.isEmpty()) {
      PsiElement element = file.findElementAt(offset);
      if (element == null) return;
      textRange = refineRange(documentText, element.getTextRange());
      if (textRange == null) return;
    }
    CharSequence text = textRange.subSequence(documentText);
    List<Integer> startOffsets = findStartOffsets(documentText, text);

    List<CaretState> caretStates = new ArrayList<>();
    for (Integer startOffset : startOffsets) {
      int caretOffset = startOffset + offset - textRange.getStartOffset();
      LogicalPosition selectionStart = null, selectionEnd = null;
//      if (!selectionRange.isEmpty()) {
        selectionStart = editor.offsetToLogicalPosition(startOffset);
        selectionEnd = editor.offsetToLogicalPosition(startOffset + text.length());
//      }
      CaretState caretState = new CaretState(editor.offsetToLogicalPosition(caretOffset), selectionStart, selectionEnd);
      caretStates.add(caretState);
    }
    editor.getCaretModel().setCaretsAndSelections(caretStates);
  }

  private TextRange refineRange(CharSequence documentText, TextRange textRange) {
    int start = textRange.getStartOffset();
    int end = textRange.getEndOffset();
    String skipChars = "/\\$ \t\r\n";
    while (start < end && StringUtil.containsChar(skipChars, documentText.charAt(start))) {
      start++;
    }
    while (end > start && StringUtil.containsChar(skipChars, documentText.charAt(end - 1))) {
      end--;
    }
    return start < end ? new TextRange(start, end) : null;
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
