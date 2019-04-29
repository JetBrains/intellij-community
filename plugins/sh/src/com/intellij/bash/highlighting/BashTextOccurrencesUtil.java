package com.intellij.bash.highlighting;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class BashTextOccurrencesUtil {

  private BashTextOccurrencesUtil() {
  }

  @Nullable
  public static TextRange findTextRangeOfIdentifierAtCaret(@NotNull Editor editor) {
    List<Caret> carets = editor.getCaretModel().getAllCarets();
    if (carets.size() != 1) return null;
    Caret caret = carets.get(0);
    if (caret.hasSelection()) {
      return null;
    }
    return SelectWordUtil.getWordSelectionRange(editor.getDocument().getImmutableCharSequence(),
        caret.getOffset(),
        BashTextOccurrencesUtil::isWordPartCondition);
  }

  private static boolean isWordPartCondition(char ch) {
    return Character.isLetterOrDigit(ch) || StringUtil.containsChar("_.", ch);
  }

  @NotNull
  public static List<TextRange> findAllOccurrences(@NotNull CharSequence documentText, @NotNull CharSequence textToFind,
                                            boolean matchExactWordsOnly) {
    List<TextRange> result = new ArrayList<>();
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
