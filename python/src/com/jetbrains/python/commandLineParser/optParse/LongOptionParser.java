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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supports --long-option-style-with=value
 *
 * @author Ilya.Kazakevich
 */
final class LongOptionParser extends OptionParserRegexBased {
  @NotNull
  private static final Pattern LONG_OPT_PATTERN = Pattern.compile("^((--[a-zA-Z0-9-]+)=?)");

  LongOptionParser() {
    super(LONG_OPT_PATTERN);
  }

  @NotNull
  @Override
  protected Pair<String, String> getOptionTextAndNameFromMatcher(@NotNull final Matcher matcher) {
    return Pair.create(matcher.group(1), matcher.group(2));
  }
}
