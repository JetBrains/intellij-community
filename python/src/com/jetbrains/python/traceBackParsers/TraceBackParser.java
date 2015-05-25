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
package com.jetbrains.python.traceBackParsers;

import com.jetbrains.python.run.PyTracebackParser;
import com.jetbrains.python.testing.pytest.PyTestTracebackParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parent of all traceback filters. Extend it and your impl. to {@link #PARSERS}.
 * When implement, provide {@link Pattern pattern}, and your code will be called only if line matches this pattern
 *
 * @author Ilya.Kazakevich
 */
public abstract class TraceBackParser {
  @NotNull
  private final Pattern myPattern;

  /**
   * @param pattern pattern to be used to match line.
   */
  protected TraceBackParser(@NotNull final Pattern pattern) {
    myPattern = pattern;
  }


  @NotNull // TODO: use EP instead?
  @SuppressWarnings("PublicStaticArrayField") // Noone will change it, anyway.
  public static final TraceBackParser[] PARSERS = {new PyTestTracebackParser(), new PyTracebackParser()};


  /**
   * Searches for link in line
   *
   * @param line line to search link in
   * @return line info (if found)
   */
  @Nullable
  public final LinkInTrace findLinkInTrace(@NotNull final String line) {
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
  @NotNull
  protected abstract LinkInTrace findLinkInTrace(@NotNull String line, @NotNull Matcher matchedMatcher);
}
