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

import com.intellij.openapi.util.TextRange;
import com.intellij.spellchecker.util.Strings;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IdentifierSplitter extends BaseSplitter {
  private static final IdentifierSplitter INSTANCE = new IdentifierSplitter();
  
  public static IdentifierSplitter getInstance() {
    return INSTANCE;
  }

  @NonNls
  private static final Pattern WORD = Pattern.compile("\\b\\p{L}*'?\\p{L}*");


  @NonNls
  private static final Pattern WORD_IN_QUOTES = Pattern.compile("'([^']*)'");

  @Override
  public void split(@Nullable String text, @NotNull TextRange range, Consumer<TextRange> consumer) {
    if (text == null || range.getLength() < 1 || range.getStartOffset() < 0) {
      return;
    }

    List<TextRange> extracted = excludeByPattern(text, range, WORD_IN_QUOTES, 1);

    for (TextRange textRange : extracted) {
      List<TextRange> words = splitByCase(text, textRange);

      if (words.size() == 0) {
        continue;
      }

      if (words.size() == 1) {
        addWord(consumer, false, words.get(0));
        continue;
      }

      boolean isCapitalized = Strings.isCapitalized(text, words.get(0));
      boolean containsShortWord = containsShortWord(words);

      if (isCapitalized && containsShortWord) {
        continue;
      }

      boolean isAllWordsAreUpperCased = isAllWordsAreUpperCased(text, words);

      for (TextRange word : words) {
        boolean uc = Strings.isUpperCased(text, word);
        boolean flag = (uc && !isAllWordsAreUpperCased);
        Matcher matcher = WORD.matcher(text.substring(word.getStartOffset(), word.getEndOffset()));
        if (matcher.find()) {
          TextRange found = matcherRange(word, matcher);
          addWord(consumer, flag, found);
        }
      }
    }
  }

  @NotNull
  private static List<TextRange> splitByCase(@NotNull String text, @NotNull TextRange range) {
    //System.out.println("text = " + text + " range = " + range);
    List<TextRange> result = new ArrayList<>();
    int i = range.getStartOffset();
    int s = -1;
    int prevType = Character.MATH_SYMBOL;
    while (i < range.getEndOffset()) {
      final char ch = text.charAt(i);
      if (ch >= '\u3040' && ch <= '\u309f' || // Hiragana
          ch >= '\u30A0' && ch <= '\u30ff' || // Katakana
          ch >= '\u4E00' && ch <= '\u9FFF' || // CJK Unified ideographs
          ch >= '\uF900' && ch <= '\uFAFF' || // CJK Compatibility Ideographs
          ch >= '\uFF00' && ch <= '\uFFEF' //Halfwidth and Fullwidth Forms of Katakana & Fullwidth ASCII variants
         ) {
        if (s >= 0) {
          add(text, result, i, s);
          s = -1;
        }
        prevType = Character.MATH_SYMBOL;
        ++i;
        continue;
      }

      final int type = Character.getType(ch);
      if (type == Character.LOWERCASE_LETTER ||
          type == Character.UPPERCASE_LETTER ||
          type == Character.TITLECASE_LETTER ||
          type == Character.OTHER_LETTER ||
          type == Character.MODIFIER_LETTER ||
          type == Character.OTHER_PUNCTUATION
        ) {
        //letter
        if (s < 0) {
          //start
          s = i;
        }
        else if (s >= 0 && type == Character.UPPERCASE_LETTER && prevType == Character.LOWERCASE_LETTER) {
          //a|Camel
          add(text, result, i, s);
          s = i;
        }
        else if (i - s >= 1 && type == Character.LOWERCASE_LETTER && prevType == Character.UPPERCASE_LETTER) {
          //CAPITALN|ext
          add(text, result, i - 1, s);
          s = i - 1;
        }
      }
      else if (s >= 0) {
        //non-letter
        add(text, result, i, s);
        s = -1;
      }
      prevType = type;
      i++;
    }
    //remainder
    if (s >= 0) {
      add(text, result, i, s);
    }
    return result;
  }

  private static void add(String text, List<TextRange> result, int i, int s) {
    if (i - s > 3) {
      final TextRange textRange = new TextRange(s, i);
      //System.out.println("textRange = " + textRange + " = "+ textRange.substring(text));
      result.add(textRange);
    }
  }
}
