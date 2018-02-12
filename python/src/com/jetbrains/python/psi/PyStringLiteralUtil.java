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

import com.google.common.collect.ImmutableList;
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
public class PyStringLiteralUtil {
  /**
   * Valid string prefix characters (lowercased) as defined in Python lexer.
   */
  public static final String PREFIX_CHARACTERS = "ubcrf";
  /**
   * Maximum length of a string prefix as defined in Python lexer.
   */
  public static final int MAX_PREFIX_LENGTH = 3;
  private static final ImmutableList<String> QUOTES = ImmutableList.of("'''", "\"\"\"", "'", "\"");

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
    return PyTokenTypes.STRING_NODES.contains(lexer.getTokenType()) && lexer.getTokenEnd() == lexer.getBufferEnd();
  }

  /**
   * Handles unicode and raw strings
   *
   * @param text
   * @return false if no quotes found, true otherwise
   *         sdfs -> false
   *         ur'x' -> true
   *         "string" -> true
   */

  public static boolean isQuoted(@Nullable String text) {
    return text != null && getQuotes(text) != null;
  }

  /**
   * Handles unicode and raw strings
   *
   * @param text
   * @return open and close quote (including raw/unicode prefixes), null if no quotes present in string
   *         'string' -> (', ')
   *         UR"unicode raw string" -> (UR", ")
   */
  @Nullable
  public static Pair<String, String> getQuotes(@NotNull final String text) {
    final String prefix = getPrefix(text);
    final String mainText = text.substring(prefix.length());
    for (String quote : QUOTES) {
      final Pair<String, String> quotes = getQuotes(mainText, prefix, quote);
      if (quotes != null) {
        return quotes;
      }
    }
    return null;
  }

  /**
   * Finds the end offset of the string prefix starting from {@code startOffset} in the given char sequence. 
   * String prefix may contain only up to {@link #MAX_PREFIX_LENGTH} characters from {@link #PREFIX_CHARACTERS}
   * (case insensitively).  
   * 
   * @return end offset of found string prefix
   */
  public static int getPrefixEndOffset(@NotNull CharSequence text, int startOffset) {
    int offset;
    for (offset = startOffset; offset < Math.min(startOffset + MAX_PREFIX_LENGTH, text.length()); offset++) {
      if (PREFIX_CHARACTERS.indexOf(Character.toLowerCase(text.charAt(offset))) < 0) {
        break;
      }
    }
    return offset;
  }

  @NotNull
  public static String getPrefix(@NotNull CharSequence text) {
    return getPrefix(text, 0);
  }

  /**
   * Extracts string prefix from the given char sequence using {@link #getPrefixEndOffset(CharSequence, int)}.
   *
   * @return extracted string prefix
   * @see #getPrefixEndOffset(CharSequence, int)
   */
  @NotNull
  public static String getPrefix(@NotNull CharSequence text, int startOffset) {
    return text.subSequence(startOffset, getPrefixEndOffset(text, startOffset)).toString();
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

  @Nullable
  private static Pair<String, String> getQuotes(@NotNull String text, @NotNull String prefix, @NotNull String quote) {
    final int length = text.length();
    final int n = quote.length();
    if (length >= 2 * n && text.startsWith(quote) && text.endsWith(quote)) {
      return Pair.create(prefix + text.substring(0, n), text.substring(length - n));
    }
    return null;
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

  public static String stripQuotesAroundValue(String text) {
    Pair<String, String> quotes = getQuotes(text);
    if (quotes == null) {
      return text;
    }

    return text.substring(quotes.first.length(), text.length() - quotes.second.length());
  }
}
