package com.intellij.sh.highlighting;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ShTextOccurrencesUtil {
  private ShTextOccurrencesUtil() {
  }

  @Nullable
  public static TextRange findTextRangeOfIdentifierAtCaret(@NotNull Editor editor) {
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    if (caret.hasSelection()) {
      TextRange textRange = TextRange.create(caret.getSelectionStart(), caret.getSelectionEnd());
      CharSequence subSequence = textRange.subSequence(editor.getDocument().getCharsSequence());
      return StringUtil.isEmptyOrSpaces(subSequence) ? null : textRange;
    }
    return SelectWordUtil.getWordSelectionRange(editor.getDocument().getImmutableCharSequence(),
        caret.getOffset(),
        ShTextOccurrencesUtil::isWordPartCondition);
  }

  private static boolean isWordPartCondition(char ch) {
    return ch == '_' || Character.isLetterOrDigit(ch);
  }

  @NotNull
  public static List<TextRange> findAllOccurrences(@NotNull CharSequence documentText,
                                                   @NotNull CharSequence textToFind,
                                                   boolean matchExactWordsOnly) {
    List<TextRange> result = ContainerUtil.newSmartList();
    int offset = StringUtil.indexOf(documentText, textToFind);
    while (offset >= 0) {
      TextRange textRange = TextRange.create(offset, offset + textToFind.length());
      if (!matchExactWordsOnly || !isWordExpandableOutside(documentText, textRange)) {
        result.add(textRange);
      }
      offset = StringUtil.indexOf(documentText, textToFind, offset + textToFind.length());
    }
    return result;
  }

  private static boolean isWordExpandableOutside(@NotNull CharSequence documentText, @NotNull TextRange textRange) {
    if (textRange.getStartOffset() > 0) {
      char ch = documentText.charAt(textRange.getStartOffset() - 1);
      if (isWordPartCondition(ch)) {
        return true;
      }
    }
    if (textRange.getEndOffset() < documentText.length()) {
      char ch = documentText.charAt(textRange.getEndOffset());
      return isWordPartCondition(ch);
    }
    return false;
  }
}
