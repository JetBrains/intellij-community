/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.optParse;

import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Word with range position. To be used to mark some part of text or split it.
 *
 * @author Ilya.Kazakevich
 */
public final class WordWithPosition extends Range<Integer> {
  @NotNull
  private final String myWord;

  /**
   * Creates word with beam (it has start, but it is infinite)
   * @param word word
   * @param from start
   */
  public WordWithPosition(@NotNull final String word, final int from) {
    this(word, from, Integer.MAX_VALUE);
  }

  /**
   * @param word text
   * @param from from where
   * @param to   to where
   */
  public WordWithPosition(@NotNull final String word, final int from, final int to) {
    super(from, to);
    myWord = word;
  }

  @NotNull
  public String getText() {
    return myWord;
  }

  /**
   * Returns texts only from collection of instances of this class
   *
   * @param words collection of instances of this class
   * @return collection of strings (text)
   */
  @NotNull
  public static List<String> fetchText(@NotNull final Collection<WordWithPosition> words) {
    final List<String> result = new ArrayList<String>(words.size());
    for (final WordWithPosition word : words) {
      result.add(word.myWord);
    }
    return result;
  }

  /**
   * Creates new instance with all fields copied but text
   *
   * @param newText new test to add
   * @return new instance
   */
  @NotNull
  public WordWithPosition copyWithDifferentText(@NotNull final String newText) {
    return new WordWithPosition(newText, getFrom(), getTo());
  }

  @Override
  public String toString() {
    return "WordWithPosition{" +
           "myWord='" + myWord + '\'' +
           ", myFrom=" + getFrom() +
           ", myTo=" + getTo() +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    WordWithPosition position = (WordWithPosition)o;

    if (getFrom() != position.getFrom()) return false;
    if (getTo() != position.getTo()) return false;
    if (!myWord.equals(position.myWord)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myWord.hashCode();
    result = 31 * result + getFrom();
    result = 31 * result + getTo();
    return result;
  }


  /**
   * Tool that splits text into parts using one or more whitespace as delimiter.
   * Each part contains text and boundaries (from and to)
   *
   * @param text text to split
   * @return parse result
   */
  @NotNull
  static List<WordWithPosition> splitText(@NotNull final String text) {
    // TODO: Rewrite using regex or scanner?
    int position = 0;
    int wordStart = -1;
    final List<WordWithPosition> parts = new ArrayList<WordWithPosition>();
    for (final char c : text.toCharArray()) {
      if (Character.isWhitespace(c) && wordStart != -1) {
        // Close word
        parts.add(new WordWithPosition(text.substring(wordStart, position), wordStart, position));
        wordStart = -1;
      }
      else if (!Character.isWhitespace(c) && wordStart == -1) {
        // Start word
        wordStart = position;
      }

      position++;
    }
    if (wordStart != -1) {
      // Adding last word
      parts.add(new WordWithPosition(text.substring(wordStart), wordStart, position));
    }
    return parts;
  }
}
