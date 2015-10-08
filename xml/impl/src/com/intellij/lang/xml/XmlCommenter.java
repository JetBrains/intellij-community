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
package com.intellij.lang.xml;

import com.intellij.codeInsight.generation.EscapingCommenter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class XmlCommenter implements EscapingCommenter {

  private static final String DOUBLE_DASH = "--";
  private static final String ESCAPED_DOUBLE_DASH = "&#45;&#45;";
  private static final String GT = ">";
  private static final String ESCAPED_GT = "&gt;";

  @Override
  public String getLineCommentPrefix() {
    return null;
  }

  @NotNull
  @Override
  public String getBlockCommentPrefix() {
    return "<!--";
  }

  @NotNull
  @Override
  public String getBlockCommentSuffix() {
    return "-->";
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return "&lt;!&ndash;";
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return "&ndash;&gt;";
  }

  @Override
  public void escape(Document document, RangeMarker range) {
    String prefix = getBlockCommentPrefix();
    String suffix = getBlockCommentSuffix();

    int start = range.getStartOffset();
    int prefixStart = start = CharArrayUtil.shiftForward(document.getCharsSequence(), start, " \t\n");
    if (CharArrayUtil.regionMatches(document.getCharsSequence(), prefixStart, prefix)) {
      start += prefix.length();
    }
    int end = range.getEndOffset();
    if (CharArrayUtil.regionMatches(document.getCharsSequence(), end - suffix.length(), suffix)) {
      end -= suffix.length();
    }
    if (start >= end) return;

    for (int i = end - DOUBLE_DASH.length(); i >= start; i--) {
      if (CharArrayUtil.regionMatches(document.getCharsSequence(), i, DOUBLE_DASH) &&
          !CharArrayUtil.regionMatches(document.getCharsSequence(), i, suffix) &&
          !CharArrayUtil.regionMatches(document.getCharsSequence(), i - 2, prefix)) {
        document.replaceString(i, i + DOUBLE_DASH.length(), ESCAPED_DOUBLE_DASH);
      }
    }
    if (CharArrayUtil.regionMatches(document.getCharsSequence(), start, GT)) {
      document.replaceString(start, start + GT.length(), ESCAPED_GT);
    }
    if (CharArrayUtil.regionMatches(document.getCharsSequence(), prefixStart, prefix + "-")) {
      document.insertString(start, " ");
    }
    if (CharArrayUtil.regionMatches(document.getCharsSequence(), range.getEndOffset() - suffix.length() - 1, "-" + suffix)) {
      document.insertString(range.getEndOffset() - suffix.length(), " ");
    }
  }

  @Override
  public void unescape(Document document, RangeMarker range) {
    final int start = range.getStartOffset();
    for (int i = range.getEndOffset(); i >= start; i--) {
      if (CharArrayUtil.regionMatches(document.getCharsSequence(), i, ESCAPED_DOUBLE_DASH)) {
        document.replaceString(i, i + ESCAPED_DOUBLE_DASH.length(), DOUBLE_DASH);
      }
    }
    if (CharArrayUtil.regionMatches(document.getCharsSequence(), start, ESCAPED_GT)) {
      document.replaceString(start, start + ESCAPED_GT.length(), GT);
    }
  }
}
