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
package com.jetbrains.python.commandInterface.commandsWithArgs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Information about command {@link com.jetbrains.python.commandInterface.commandsWithArgs.Argument arguments} and their value
 * validation.
 * Check optparse manual, package info and {@link com.jetbrains.python.commandInterface.commandsWithArgs.Argument}
 * manual for more info about arguments.
 *
 * @author Ilya.Kazakevich
 */
public interface ArgumentsInfo {
  /**
   * Returns argument by its position.
   *
   * @param argumentPosition argument position
   * @return null if no argument value is available at this position. Returns argument otherwise.
   */
  @Nullable
  Argument getArgument(int argumentPosition);


  /**
   * Validates argument <strong>values</strong>.
   * Values should be provided as list. I.e. for <pre>my_command foo bar</pre> there should be list of "foo, bar".
   *
   * @param argumentValuesToCheck values to check
   * @return validation result
   */
  @NotNull
  ArgumentsValuesValidationInfo validateArgumentValues(@NotNull final List<String> argumentValuesToCheck);
}
