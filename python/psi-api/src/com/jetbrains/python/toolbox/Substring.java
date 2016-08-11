/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Substring with explicit offsets within its parent string.
 * <p/>
 * Regular java.lang.String objects share a single char buffer for results of substring(), trim(), etc., but the offset and count
 * fields of Strings are unfortunately private.
 *
 * @author vlan
 */
public class Substring implements CharSequence {
  private static final Pattern RE_NL = Pattern.compile("(\\r?\\n)");

  @NotNull private final String myString;
  private final int myStartOffset;
  private final int myEndOffset;

  public Substring(@NotNull String s) {
    this(s, 0, s.length());
  }

  public Substring(@NotNull String s, int start, int end) {
    myString = s;
    myStartOffset = start;
    myEndOffset = end;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof String) {
      return toString().equals(o);
    }
    else if (o instanceof Substring) {
      return toString().equals(o.toString());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return toString().hashCode();
  }

  @NotNull
  @Override
  public String toString() {
    return getValue();
  }

  @NotNull
  public String getValue() {
    return getTextRange().substring(myString);
  }

  @NotNull
  public String getSuperString() {
    return myString;
  }

  @NotNull
  public TextRange getTextRange() {
    return TextRange.create(myStartOffset, myEndOffset);
  }

  @NotNull
  public List<Substring> split(@NotNull String regex) {
    return split(regex, Integer.MAX_VALUE);
  }

  @NotNull
  public List<Substring> split(@NotNull String regex, int maxSplits) {
    return split(Pattern.compile(regex), maxSplits);
  }

  @NotNull
  public List<Substring> split(@NotNull Pattern pattern) {
    return split(pattern, Integer.MAX_VALUE);
  }

  @NotNull
  public List<Substring> split(@NotNull Pattern pattern, int maxSplits) {
    final List<Substring> result = new ArrayList<>();
    final Matcher m = pattern.matcher(myString);
    int start = myStartOffset;
    int end = myEndOffset;
    int splitCount = 0;
    if (m.find(start)) {
      do {
        splitCount++;
        end = m.start();
        result.add(createAnotherSubstring(start, Math.min(end, myEndOffset)));
        start = m.end();
      }
      while (end < myEndOffset && m.find() && splitCount < maxSplits);
      if (start <= myEndOffset) {
        result.add(createAnotherSubstring(start, myEndOffset));
      }
    }
    else {
      result.add(createAnotherSubstring(start, end));
    }
    return result;
  }

  @NotNull
  public List<Substring> splitLines() {
    return split(RE_NL);
  }

  @NotNull
  public Substring trim() {
    return trimLeft().trimRight();
  }

  @NotNull
  public Substring trimLeft() {
    int start;
    for (start = myStartOffset; start < myEndOffset && myString.charAt(start) <= '\u0020'; start++) { /*empty*/ }
    return createAnotherSubstring(start, myEndOffset);
  }

  @NotNull
  public Substring trimRight() {
    int end;
    for (end = myEndOffset - 1; end > myStartOffset && myString.charAt(end) <= '\u0020'; end--) { /* empty */ }
    return createAnotherSubstring(myStartOffset, end + 1);
  }

  @NotNull
  public Substring getMatcherGroup(@NotNull Matcher m, int group) {
    return substring(m.start(group), m.end(group));
  }

  @Override
  public int length() {
    return myEndOffset - myStartOffset;
  }

  public boolean isEmpty() {
    return length() <= 0;
  }

  @Override
  public char charAt(int i) {
    return myString.charAt(myStartOffset + i);
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    return substring(start, end);
  }

  public boolean startsWith(@NotNull String prefix) {
    return indexOf(prefix) == 0;
  }

  public boolean endsWith(@NotNull String prefix) {
    return myString.lastIndexOf(prefix) == length() - prefix.length();
  }

  public int indexOf(@NotNull String s) {
    int n = myString.indexOf(s, myStartOffset);
    return n >= 0 && n < myEndOffset ? n - myStartOffset : -1;
  }

  public boolean contains(@NotNull String s) {
    return indexOf(s) >= 0;
  }

  @NotNull
  @SuppressWarnings({"MethodNamesDifferingOnlyByCase"})
  public Substring substring(int start) {
    return substring(start, length());
  }

  @NotNull
  @SuppressWarnings({"MethodNamesDifferingOnlyByCase"})
  public Substring substring(int start, int end) {
    return createAnotherSubstring(myStartOffset + start, myStartOffset + end);
  }

  @NotNull
  public String concatTrimmedLines(@NotNull String separator) {
    final StringBuilder b = new StringBuilder();
    List<Substring> lines = splitLines();
    final int n = lines.size();
    for (int i = 0; i < n; i++) {
      b.append(lines.get(i).trim().toString());
      if (i < n - 1) {
        b.append(separator);
      }
    }
    return b.toString();
  }

  @NotNull
  private Substring createAnotherSubstring(int start, int end) {
    return new Substring(myString, start, end);
  }

  /**
   * If both substrings share the same origin, returns new substring that includes both of them.
   */
  @NotNull
  public Substring union(@NotNull Substring other) {
    if (!myString.equals(other.myString)) {
      throw new IllegalArgumentException(String.format("Substrings '%s' and '%s' must belong to the same origin", this, other));
    }
    final TextRange unionRange = getTextRange().union(other.getTextRange());
    return new Substring(getSuperString(), unionRange.getStartOffset(), unionRange.getEndOffset());
  }

  public int getStartOffset() {
    return myStartOffset;
  }

  public int getStartLine() {
    return StringUtil.offsetToLineNumber(myString, myStartOffset);
  }

  public int getEndOffset() {
    return myEndOffset;
  }

  public int getEndLine() {
    return StringUtil.offsetToLineNumber(myString, myEndOffset);
  }
}
