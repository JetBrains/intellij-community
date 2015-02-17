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

/**
 * Engine that knows how to deal with option of certain style (like long and short)
 *
 * @author Ilya.Kazakevich
 */
interface OptionParser {
  /**
   * @param optionText text to parse (like --foo=bar)
   * @return null if option can't be parsed. Otherwise pair of [option_text, option_name]. That may match each other in some cases.
   */
  @Nullable
  Pair<String, String> findOptionTextAndName(@NotNull String optionText);
}
