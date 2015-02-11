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
 * Engine that knows how to deal with option of certain style
 *
 * @author Ilya.Kazakevich
 */
interface OptionParser {
  /**
   * Checks if some text that looks like and option is really option
   *
   * @param availableOptions all available options
   * @param textToCheck      text believed to be an option like "--foo=123"
   * @return [option->argument_value] pair or null if provided text is not an option. ArgValue may also be null if not provided
   */
  @Nullable
  Pair<Option, String> findOptionAndValue(@NotNull List<Option> availableOptions, @NotNull String textToCheck);
}
