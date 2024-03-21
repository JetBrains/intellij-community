// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.traceBackParsers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter that uses regexps
 *
 * @author Ilya.Kazakevich
 */
public abstract class TraceBackParserAdapter implements TraceBackParser {
  /**
   * We do not search anything in line longer than this, because it makes no sense to search something in so long lines
   */
  private static final int MAX_LINE_TO_PARSE = 5000;
  private final @NotNull Pattern myPattern;

  /**
   * @param pattern pattern to be used to match line.
   */
  protected TraceBackParserAdapter(final @NotNull Pattern pattern) {
    myPattern = pattern;
  }


  @Override
  public final @Nullable LinkInTrace findLinkInTrace(@NotNull String line) {
    if (line.length() > MAX_LINE_TO_PARSE) {
      // Cut down line is too long to parse (to prevent freeze)
      //noinspection AssignmentToMethodParameter
      line = line.substring(0, MAX_LINE_TO_PARSE);
    }
    final Matcher matcher = myPattern.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    return findLinkInTrace(line, matcher);
  }


  /**
   * Fetches link from line
   *
   * @param line           line to search link in
   * @param matchedMatcher regex matcher that found link
   * @return line info
   */
  protected abstract @NotNull LinkInTrace findLinkInTrace(@NotNull String line, @NotNull Matcher matchedMatcher);
}
