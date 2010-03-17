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
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.spellchecker.util.Strings;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class IdentifierSplitter extends BaseSplitter{

  @NonNls
  private static final Pattern WORD = Pattern.compile("\\b\\p{L}*'?\\p{L}*");


  @Nullable
  public List<CheckArea> split(@Nullable String text, @NotNull TextRange range) {

    if (text == null || range.getLength() < 1) {
      return null;
    }
    List<CheckArea> results = new ArrayList<CheckArea>();
    String word = text.substring(range.getStartOffset(), range.getEndOffset());
    String[] words = splitNameIntoWords(word);
    if (words == null || words.length == 0) {
      return results;
    }

    if (words.length == 1) {
      Matcher matcher = WORD.matcher(words[0]);
      if (matcher.find()) {
        TextRange found = matcherRange(range, matcher);
        addWord(text, results, false, found);
      }
      return results;
    }

    boolean isCapitalized = Strings.isCapitalized(words[0]);
    boolean containsShortWord = containsShortWord(words);

    if (isCapitalized && containsShortWord) {
      results.add(new CheckArea(text, range, true));
      return results;
    }

    boolean isAllWordsAreUpperCased = isAllWordsAreUpperCased(words);
    int index = 0;
    for (String s : words) {
      int start = word.indexOf(s, index);
      int end = start + s.length();
      boolean isUpperCase = Strings.isUpperCase(s);
      boolean flag = (isUpperCase && !isAllWordsAreUpperCased);
      Matcher matcher = WORD.matcher(s);
      if (matcher.find()) {
        TextRange found = matcherRange(subRange(range, start, end), matcher);
        addWord(text, results, flag, found);
      }
      index = end;
    }
    return results;
  }


//TODO[shkate] - rewrite using TextRanges instead of strings
  public static String[] splitNameIntoWords(@NotNull String name) {
    final String[] underlineDelimited = name.split("_");
    List<String> result = new ArrayList<String>();
    for (String word : underlineDelimited) {
      addAllWords(word, result);
    }
    return ArrayUtil.toStringArray(result);
  }

  private enum WordState { NO_WORD, PREV_UC, WORD }

  private static void addAllWords(String word, List<String> result) {
    CharacterIterator it = new StringCharacterIterator(word);
    StringBuffer b = new StringBuffer();
    WordState state = WordState.NO_WORD;
    char curPrevUC = '\0';
    for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
      switch (state) {
        case NO_WORD:
          if (!Character.isUpperCase(c)) {
            b.append(c);
            state = WordState.WORD;
          }
          else {
            state = WordState.PREV_UC;
            curPrevUC = c;
          }
          break;
        case PREV_UC:
          if (!Character.isUpperCase(c)) {
            b = startNewWord(result, b, curPrevUC);
            b.append(c);
            state = WordState.WORD;
          }
          else {
            b.append(curPrevUC);
            state = WordState.PREV_UC;
            curPrevUC = c;
          }
          break;
        case WORD:
          if (Character.isUpperCase(c)) {
            startNewWord(result, b, c);
            b.setLength(0);
            state = WordState.PREV_UC;
            curPrevUC = c;
          }
          else {
            b.append(c);
          }
          break;
      }
    }
    if (state == WordState.PREV_UC) {
      b.append(curPrevUC);
    }
    result.add(b.toString());
  }

  private static StringBuffer startNewWord(List<String> result, StringBuffer b, char c) {
    if (b.length() > 0) {
      result.add(b.toString());
    }
    b = new StringBuffer();
    b.append(c);
    return b;
  }
  

}
