/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.inspections;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public abstract class BaseSplitter implements Splitter {

  public static final int MIN_RANGE_LENGTH = 3;


  protected static void addWord(@NotNull String text, @NotNull List<CheckArea> results, boolean ignore, @Nullable TextRange found) {
    if (found == null || ignore) {
      return;
    }
    boolean tooShort = (found.getEndOffset() - found.getStartOffset()) <= MIN_RANGE_LENGTH;
    if (tooShort) {
      return;
    }
    results.add(new CheckArea(text, found, false));
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

  public List<CheckArea> split(@Nullable String text) {
    if (text == null) {
      return null;
    }
    return split(text, new TextRange(0, text.length()));
  }

  protected static boolean tooSmall(int from, int till) {
    return till - from <= MIN_RANGE_LENGTH;
  }

  @Nullable
  static protected List<TextRange> excludeByPattern(String text, TextRange range, @NotNull Pattern toExclude, int groupToInclude) {
    List<TextRange> toCheck = new ArrayList<TextRange>();
    int from = range.getStartOffset();
    int till;
    boolean addLast = true;
    Matcher matcher = toExclude.matcher(text.substring(range.getStartOffset(), range.getEndOffset()));
    while (matcher.find()) {

      checkCancelled();

      TextRange found = matcherRange(range, matcher);
      till = found.getStartOffset() - 1;
      if (range.getEndOffset() - found.getEndOffset() < MIN_RANGE_LENGTH) {
        addLast = false;
      }
      if (!tooSmall(from, till)) {
        toCheck.add(new TextRange(from, till));
      }
      if (groupToInclude > 0) {
        TextRange contentFound = matcherRange(range, matcher, groupToInclude);
        if (tooSmall(contentFound.getEndOffset(), contentFound.getStartOffset())) {
          toCheck.add(new TextRange(contentFound.getStartOffset(), contentFound.getEndOffset()));
        }
      }
      from = found.getEndOffset();
    }
    till = range.getEndOffset();
    if (tooSmall(from, till)) {
      return toCheck;
    }
    if (addLast) {
      toCheck.add(new TextRange(from, till));
    }
    return toCheck;
  }

  public static void checkCancelled() {
    ProgressManager.checkCanceled();
  }


}
