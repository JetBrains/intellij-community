// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PythonLexer;
import org.jetbrains.annotations.NotNull;

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
  public static @NotNull String getStringValue(@NotNull String s) {
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
  public static @NotNull TextRange getContentRange(@NotNull String text) {
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
   * @return whether the given prefix contains either 't' or 'T' character
   */
  public static boolean isTemplatePrefix(@NotNull String prefix) {
    return StringUtil.indexOfIgnoreCase(prefix, 't', 0) >= 0;
  }

  /**
   * @return alternative quote character, i.e. " for ' and ' for "
   */
  public static char flipQuote(char quote) {
    return quote == '"' ? '\'' : '"';
  }
}
