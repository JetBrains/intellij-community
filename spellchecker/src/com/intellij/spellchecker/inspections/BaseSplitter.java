// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseSplitter implements Splitter {
  public static final int MIN_RANGE_LENGTH = 3;

  protected static void addWord(@NotNull Consumer<? super TextRange> consumer, boolean ignore, @Nullable TextRange found) {
    if (found == null || ignore) {
      return;
    }
    boolean tooShort = (found.getEndOffset() - found.getStartOffset()) <= MIN_RANGE_LENGTH;
    if (tooShort) {
      return;
    }

    ProgressManager.checkCanceled();
    consumer.consume(found);
  }

  protected static boolean isAllWordsAreUpperCased(@NotNull String text, @NotNull List<? extends TextRange> words) {
    for (TextRange word : words) {
      CharacterIterator it = new StringCharacterIterator(text, word.getStartOffset(), word.getEndOffset(), word.getStartOffset());
      for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
        if (!Character.isUpperCase(c)) {
          return false;
        }
      }
    }
    return true;
  }

  protected static boolean containsShortWord(@NotNull List<? extends TextRange> words) {
    for (TextRange word : words) {
      if (word.getLength() < MIN_RANGE_LENGTH) {
        return true;
      }
    }
    return false;
  }

  protected static @NotNull TextRange matcherRange(@NotNull TextRange range, @NotNull Matcher matcher) {
    return subRange(range, matcher.start(), matcher.end());
  }

  protected static @NotNull TextRange matcherRange(@NotNull TextRange range, @NotNull Matcher matcher, int group) {
    return subRange(range, matcher.start(group), matcher.end(group));
  }

  protected static @NotNull TextRange subRange(@NotNull TextRange range, int start, int end) {
    return TextRange.from(range.getStartOffset() + start, end - start);
  }

  protected static boolean badSize(int from, int till) {
    int l = till - from;
    return l <= MIN_RANGE_LENGTH;
  }

  protected static @NotNull List<TextRange> excludeByPattern(String text, TextRange range, @NotNull Pattern toExclude, int groupToInclude) {
    List<TextRange> toCheck = new SmartList<>();
    int from = range.getStartOffset();
    int till;
    boolean addLast = true;

    try {
      Matcher matcher = toExclude.matcher(newBombedCharSequence(text, range));
      while (matcher.find()) {
        ProgressManager.checkCanceled();

        TextRange found = matcherRange(range, matcher);
        till = found.getStartOffset();
        if (range.getEndOffset() - found.getEndOffset() < MIN_RANGE_LENGTH) {
          addLast = false;
        }
        if (!badSize(from, till)) {
          toCheck.add(new TextRange(from, till));
        }
        if (groupToInclude > 0) {
          TextRange contentFound = matcherRange(range, matcher, groupToInclude);
          if (badSize(contentFound.getEndOffset(), contentFound.getStartOffset())) {
            toCheck.add(contentFound);
          }
        }
        from = found.getEndOffset();
      }
      till = range.getEndOffset();
      if (badSize(from, till)) {
        return toCheck;
      }
      if (addLast) {
        toCheck.add(new TextRange(from, till));
      }
      return toCheck;
    }
    catch (TooLongBombedMatchingException e) {
      return Collections.singletonList(range);
    }
  }

  private static final int PROCESSING_TIME_LIMIT_MS = 500;

  /**
   * @throws TooLongBombedMatchingException in case processing is longer than {@link #PROCESSING_TIME_LIMIT_MS}
   */
  protected static CharSequence newBombedCharSequence(String text, TextRange range) {
    return newBombedCharSequence(range.substring(text));
  }

  /**
   * @throws TooLongBombedMatchingException in case processing is longer than {@link #PROCESSING_TIME_LIMIT_MS}
   */
  protected static CharSequence newBombedCharSequence(String substring) {
    return new StringUtil.BombedCharSequence(substring) {
      final long myTime = System.currentTimeMillis() + PROCESSING_TIME_LIMIT_MS;

      @Override
      protected void checkCanceled() {
        long l = System.currentTimeMillis();
        if (l >= myTime) {
          throw new TooLongBombedMatchingException();
        }
      }
    };
  }

  /**
   * @deprecated Use {@link ProgressManager#checkCanceled()}.
   */
  @Deprecated(forRemoval = true)
  public static void checkCancelled() {
    if (ApplicationManager.getApplication() != null) {
      ProgressIndicatorProvider.checkCanceled();
    }
  }

  public static class TooLongBombedMatchingException extends ProcessCanceledException {
  }
}