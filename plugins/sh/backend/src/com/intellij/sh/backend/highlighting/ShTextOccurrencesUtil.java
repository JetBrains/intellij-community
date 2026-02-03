// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend.highlighting;

import com.intellij.codeInsight.editorActions.SelectWordUtil;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.CollectConsumer;
import com.intellij.util.Consumer;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class ShTextOccurrencesUtil {
  private ShTextOccurrencesUtil() {
  }

  public static @Nullable TextRange findTextRangeOfIdentifierAtCaret(@NotNull Editor editor) {
    Caret caret = editor.getCaretModel().getPrimaryCaret();
    if (caret.hasSelection()) {
      TextRange textRange = TextRange.create(caret.getSelectionStart(), caret.getSelectionEnd());
      CharSequence subSequence = textRange.subSequence(editor.getDocument().getCharsSequence());
      return StringUtil.isEmptyOrSpaces(subSequence) || StringUtil.contains(subSequence, "\n") ? null : textRange;
    }
    return SelectWordUtil.getWordSelectionRange(editor.getDocument().getImmutableCharSequence(),
                                                caret.getOffset(),
                                                ShTextOccurrencesUtil::isWordPartCondition);
  }

  private static boolean isWordPartCondition(char ch) {
    return ch == '_' || Character.isLetterOrDigit(ch);
  }

  public static @NotNull Collection<TextRange> findAllOccurrences(@NotNull CharSequence documentText,
                                                                  @NotNull CharSequence textToFind,
                                                                  boolean matchExactWordsOnly) {
    CollectConsumer<TextRange> consumer = new CollectConsumer<>();
    consumeAllOccurrences(documentText, textToFind, matchExactWordsOnly, consumer);
    return consumer.getResult();
  }

  public static void consumeAllOccurrences(@NotNull CharSequence documentText,
                                           @NotNull CharSequence textToFind,
                                           boolean matchExactWordsOnly,
                                           Consumer<? super TextRange> consumer) {
    String pattern = textToFind.toString();
    int length = pattern.length();
    StringSearcher searcher = new StringSearcher(pattern, true, true);
    searcher.processOccurrences(documentText, value -> {
      TextRange tr = TextRange.create(value, value + length);
      if (matchExactWordsOnly && isWordExpandableOutside(documentText, tr)) return true;
      consumer.consume(tr);
      return true;
    });
  }

  private static boolean isWordExpandableOutside(@NotNull CharSequence documentText, @NotNull Segment textRange) {
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
