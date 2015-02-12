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

/**
 * Supports --long-option-style-with=value
 * @author Ilya.Kazakevich
 */
final class LongOptionParser implements OptionParser {
  @Nullable
  @Override
  public Pair<Option, String> findOptionAndValue(@NotNull final List<Option> availableOptions, @NotNull final String textToCheck) {
    if (!textToCheck.startsWith("--")) {
      return null;
    }

    for (final Option option : availableOptions) {
      for (final String longName : option.getLongNames()) {
        if (textToCheck.startsWith(longName)) {
          final String[] parts = textToCheck.split("=");
          if (parts.length == 0) {
            return Pair.create(option, null);
          }
          else {
            return Pair.create(option, parts[1]);
          }
        }
      }
    }

    return null;
  }
}
