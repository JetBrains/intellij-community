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
import com.jetbrains.python.commandInterface.command.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deals with short-style options like -b or -f
 * @author Ilya.Kazakevich
 */
final class ShortOptionParser implements OptionParser {
  /**
   * Short-style option regexp
   */
  private static final Pattern SHORT_OPT_START = Pattern.compile("^(-[a-zA-Z0-9])([^ -])?");

  @Nullable
  @Override
  public Pair<Option, String> findOptionAndValue(@NotNull final List<Option> availableOptions, @NotNull final String textToCheck) {
    final Matcher matcher = SHORT_OPT_START.matcher(textToCheck);

    if (!matcher.find()) {
      return null;
    }
    final String optionText = matcher.group(1);
    final String optionValueText = (matcher.groupCount() == 2 ? matcher.group(2) : null);


    for (final Option option : availableOptions) {
      for (final String shortName : option.getShortNames()) {
        if (optionText.equals(shortName)) {
          return Pair.create(option, optionValueText);
        }
      }
    }

    return null;
  }
}
