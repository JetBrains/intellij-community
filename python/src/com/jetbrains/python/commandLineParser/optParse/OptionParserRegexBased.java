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
package com.jetbrains.python.commandLineParser.optParse;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses options using regex and template method
 *
 * @author Ilya.Kazakevich
 */
abstract class OptionParserRegexBased implements OptionParser {
  @NotNull
  private final Pattern myPattern;

  /**
   * @param pattern regex. If option matches pattern, ({@link Matcher#find() opened matcher}) would be passed to template method
   *                {@link #getOptionTextAndNameFromMatcher(Matcher)}
   */
  protected OptionParserRegexBased(@NotNull final Pattern pattern) {
    myPattern = pattern;
  }

  @Nullable
  @Override
  public final Pair<String, String> findOptionTextAndName(@NotNull final String optionText) {
    final Matcher matcher = myPattern.matcher(optionText);
    if (!matcher.find()) {
      return null;
    }
    return getOptionTextAndNameFromMatcher(matcher);
  }

  /**
   * Obtains [option_text, option_name] from matcher
   *
   * @param matcher opened (with find() called) matcher
   * @return pair [option_text, option_name]
   */
  @NotNull
  protected abstract Pair<String, String> getOptionTextAndNameFromMatcher(@NotNull Matcher matcher);
}
