/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public final class PyStringLiteralUtil extends PyStringLiteralCoreUtil {

  private static final Logger LOG = Logger.getInstance(PyStringLiteralUtil.class);

  private PyStringLiteralUtil() {
  }

  /**
   * 'text' => text
   * "text" => text
   * text => text
   * "text => "text
   *
   * @return string without heading and trailing pair of ' or "
   */
  @NotNull
  public static String getStringValue(@NotNull String s) {
    return getStringValueTextRange(s).substring(s);
  }

  public static TextRange getStringValueTextRange(@NotNull String s) {
    final Pair<String, String> quotes = getQuotes(s);
    if (quotes != null) {
      return TextRange.create(quotes.getFirst().length(), s.length() - quotes.getSecond().length());
    }
    return TextRange.allOf(s);
  }

  /**
   * @return whether the given text is recognized as a valid string literal token by Python lexer
   */
  public static boolean isStringLiteralToken(@NotNull String text) {
    final PythonLexer lexer = new PythonLexer();
    lexer.start(text);
    return PyTokenTypes.STRING_NODES.contains(lexer.getTokenType()) && lexer.getTokenEnd() == lexer.getBufferEnd() ||
           PyTokenTypes.FSTRING_START == lexer.getTokenType();
  }

  /**
   * Returns the range of the string literal text between the opening quote and the closing one.
   * If the closing quote is either missing or mismatched, this range spans until the end of the literal.
   */
  @NotNull
  public static TextRange getContentRange(@NotNull String text) {
    LOG.assertTrue(isStringLiteralToken(text), "Text of a single string literal node expected");
    int startOffset = getPrefixLength(text);
    int delimiterLength = 1;
    final String afterPrefix = text.substring(startOffset);
    if (afterPrefix.startsWith("\"\"\"") || afterPrefix.startsWith("'''")) {
      delimiterLength = 3;
    }
    final String delimiter = text.substring(startOffset, startOffset + delimiterLength);
    startOffset += delimiterLength;
    int endOffset = text.length();
    if (text.substring(startOffset).endsWith(delimiter)) {
      endOffset -= delimiterLength;
    }
    return new TextRange(startOffset, endOffset);
  }

  public static int getPrefixLength(@NotNull String text) {
    return getPrefixEndOffset(text, 0);
  }

  /**
   * @return whether the given prefix contains either 'u' or 'U' character
   */
  public static boolean isUnicodePrefix(@NotNull String prefix) {
    return StringUtil.indexOfIgnoreCase(prefix, 'u', 0) >= 0;
  }

  /**
   * @return whether the given prefix contains either 'b' or 'B' character
   */
  public static boolean isBytesPrefix(@NotNull String prefix) {
    return StringUtil.indexOfIgnoreCase(prefix, 'b', 0) >= 0;
  }

  /**
   * @return whether the given prefix contains either 'r' or 'R' character
   */
  public static boolean isRawPrefix(@NotNull String prefix) {
    return StringUtil.indexOfIgnoreCase(prefix, 'r', 0) >= 0;
  }

  /**
   * @return whether the given prefix contains either 'f' or 'F' character
   */
  public static boolean isFormattedPrefix(@NotNull String prefix) {
    return StringUtil.indexOfIgnoreCase(prefix, 'f', 0) >= 0;
  }

  /**
   * @return alternative quote character, i.e. " for ' and ' for "
   */
  public static char flipQuote(char quote) {
    return quote == '"' ? '\'' : '"';
  }

  public static TextRange getTextRange(PsiElement element) {
    if (element instanceof PyStringLiteralExpression) {
      final List<TextRange> ranges = ((PyStringLiteralExpression)element).getStringValueTextRanges();
      return ranges.get(0);
    }
    else {
      return new TextRange(0, element.getTextLength());
    }
  }

  @Nullable
  public static String getText(@Nullable PyExpression ex) {
    if (ex == null) {
      return null;
    }
    else {
      return ex.getText();
    }
  }

  @Nullable
  public static String getStringValue(@Nullable PsiElement o) {
    if (o == null) {
      return null;
    }
    if (o instanceof PyStringLiteralExpression) {
      PyStringLiteralExpression literalExpression = (PyStringLiteralExpression)o;
      return literalExpression.getStringValue();
    }
    else {
      return o.getText();
    }
  }
}
