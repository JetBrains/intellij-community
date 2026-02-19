// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class PyStringLiteralCoreUtil {
  /**
   * Valid string prefix characters (lowercased) as defined in Python lexer.
   */
  public static final String PREFIX_CHARACTERS = "ubcrft";

  /**
   * Maximum length of a string prefix as defined in Python lexer.
   */
  public static final int MAX_PREFIX_LENGTH = 3;

  private static final List<String> QUOTES = List.of("'''", "\"\"\"", "'", "\"");

  protected PyStringLiteralCoreUtil() {
  }

  /**
   * Returns a pair where the first element is the prefix combined with the opening quote and the second is the closing quote.
   * <p>
   * If the given string literal is not properly quoted, e.g. the closing quote has fewer quotes as opposed to the
   * opening one, or it's missing altogether this method returns null.
   * <p>
   * Examples:
   * <pre>
   *   ur"foo" -> ("ur, ")
   *   ur'bar -> null
   *   """baz""" -> (""", """)
   *   '''quux' -> null
   * </pre>
   */
  public static @Nullable Pair<String, String> getQuotes(@NotNull String text) {
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

  public static @NotNull String getPrefix(@NotNull CharSequence text) {
    return getPrefix(text, 0);
  }

  /**
   * Extracts string prefix from the given char sequence using {@link #getPrefixEndOffset(CharSequence, int)}.
   *
   * @return extracted string prefix
   * @see #getPrefixEndOffset(CharSequence, int)
   */
  public static @NotNull String getPrefix(@NotNull CharSequence text, int startOffset) {
    return text.subSequence(startOffset, getPrefixEndOffset(text, startOffset)).toString();
  }

  private static @Nullable Pair<String, String> getQuotes(@NotNull String text, @NotNull String prefix, @NotNull String quote) {
    final int length = text.length();
    final int n = quote.length();
    if (length >= 2 * n && text.startsWith(quote) && text.endsWith(quote)) {
      return Pair.create(prefix + text.substring(0, n), text.substring(length - n));
    }
    return null;
  }

  public static String stripQuotesAroundValue(String text) {
    Pair<String, String> quotes = getQuotes(text);
    if (quotes == null) {
      return text;
    }

    return text.substring(quotes.first.length(), text.length() - quotes.second.length());
  }

  /**
   * Handles unicode and raw strings
   *
   * @return false if no quotes found, true otherwise
   *         sdfs -> false
   *         ur'x' -> true
   *         "string" -> true
   */

  public static boolean isQuoted(@Nullable String text) {
    return text != null && getQuotes(text) != null;
  }
}
