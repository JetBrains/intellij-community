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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IdentifierSplitter extends BaseSplitter {

  @NonNls
  private static final Pattern WORD = Pattern.compile("\\b\\p{L}*'?\\p{L}*");

  @NonNls
  private static final Pattern WORD_EXT = Pattern.compile("(\\p{L}*?)_");

  public IdentifierSplitter() {

  }

  @Nullable
  public List<CheckArea> split(@Nullable String text, @NotNull TextRange range) {

    if (text == null || range.getLength() < 1) {
      return null;
    }

    List<TextRange> words = splitByCase(text, range);

    if (words == null || words.size() == 0) {
      return null;
    }

    List<CheckArea> results = new ArrayList<CheckArea>();

    if (words.size() == 1) {
      addWord(text, results, false, words.get(0));
      return results;
    }

    boolean isCapitalized = Strings.isCapitalized(text, words.get(0));
    boolean containsShortWord = containsShortWord(words);

    if (isCapitalized && containsShortWord) {
      return results;
    }

    boolean isAllWordsAreUpperCased = isAllWordsAreUpperCased(text, words);

    for (TextRange word : words) {
      boolean uc = Strings.isUpperCased(text, word);
      boolean flag = (uc && !isAllWordsAreUpperCased);
      Matcher matcher = WORD.matcher(text.substring(word.getStartOffset(), word.getEndOffset()));
      if (matcher.find()) {
        TextRange found = matcherRange(word, matcher);
        addWord(text, results, flag, found);
      }
    }
    return results;
  }

  public static List<TextRange> splitByCase(@NotNull String text, @NotNull TextRange range) {
     List<TextRange> result = new ArrayList<TextRange>();
     Matcher matcher = WORD_EXT.matcher(text.substring(range.getStartOffset(), range.getEndOffset()));
     int from = range.getStartOffset();
     while (matcher.find()) {
       TextRange found = matcherRange(range, matcher);
       TextRange foundWord = matcherRange(range, matcher, 1);
       from = found.getEndOffset();
       Strings.addAll(text, foundWord, result);
     }
     if (!tooSmall(from, range.getEndOffset())) {
       Strings.addAll(text, new TextRange(from, range.getEndOffset()), result);
     }
     return result;
   }

}
