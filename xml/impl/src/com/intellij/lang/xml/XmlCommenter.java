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
import com.intellij.openapi.util.TextRange;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class XmlCommenter implements EscapingCommenter {

  private static final String DOUBLE_DASH = "--";
  private static final String ESCAPED = "&#45;&#45;";

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
  public void escape(Document document, TextRange range) {
    for (int i = range.getEndOffset() - getBlockCommentSuffix().length() - DOUBLE_DASH.length();
         i >= range.getStartOffset() + getBlockCommentPrefix().length();
         i--) {
      if (CharArrayUtil.regionMatches(document.getCharsSequence(), i, DOUBLE_DASH) &&
          !CharArrayUtil.regionMatches(document.getCharsSequence(), i, getBlockCommentSuffix())) {
        document.replaceString(i, i + DOUBLE_DASH.length(), ESCAPED);
      }
    }
  }

  @Override
  public void unescape(Document document, TextRange range) {
    for (int i = range.getEndOffset(); i >= range.getStartOffset(); i--) {
      if (CharArrayUtil.regionMatches(document.getCharsSequence(), i, ESCAPED)) {
        document.replaceString(i, i + ESCAPED.length(), DOUBLE_DASH);
      }
    }
  }
}
