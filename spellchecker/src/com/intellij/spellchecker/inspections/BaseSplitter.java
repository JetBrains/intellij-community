// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseSplitter implements Splitter {
  public static final int MIN_RANGE_LENGTH = 3;

  protected static void addWord(@NotNull Consumer<TextRange> consumer, boolean ignore, @Nullable TextRange found) {
    if (found == null || ignore) {
      return;
    }
    boolean tooShort = (found.getEndOffset() - found.getStartOffset()) <= MIN_RANGE_LENGTH;
    if (tooShort) {
      return;
    }
    consumer.consume(found);
  }

  protected static boolean isAllWordsAreUpperCased(@NotNull String text, @NotNull List<TextRange> words) {
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

  protected static boolean containsShortWord(@NotNull List<TextRange> words) {
    for (TextRange word : words) {
      if (word.getLength() < MIN_RANGE_LENGTH) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  protected static TextRange matcherRange(@NotNull TextRange range, @NotNull Matcher matcher) {
    return subRange(range, matcher.start(), matcher.end());
  }

  @NotNull
  protected static TextRange matcherRange(@NotNull TextRange range, @NotNull Matcher matcher, int group) {
    return subRange(range, matcher.start(group), matcher.end(group));
  }

  @NotNull
  protected static TextRange subRange(@NotNull TextRange range, int start, int end) {
    return TextRange.from(range.getStartOffset() + start, end - start);
  }

  protected static boolean badSize(int from, int till) {
    int l = till - from;
    return l <= MIN_RANGE_LENGTH;
  }
  @NotNull
  static protected List<TextRange> excludeByPattern(String text, TextRange range, @NotNull Matcher matcher, int groupToInclude) {
    List<TextRange> toCheck = new SmartList<>();
    int from = range.getStartOffset();
    int till;
    boolean addLast = true;
    synchronized (matcher) { //we control all parameters
      final CharSequence input = StringUtil.newBombedCharSequence(range.substring(text), 500);
      matcher.reset(input);
      try {
        while (matcher.find()) {
          checkCancelled();
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
              toCheck.add(TextRange.create(contentFound));
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
      catch (ProcessCanceledException e) {
        return Collections.singletonList(range);
      }
    }
  }

  public static void checkCancelled() {
    if (ApplicationManager.getApplication() != null) {
      ProgressIndicatorProvider.checkCanceled();
    }
  }
}
