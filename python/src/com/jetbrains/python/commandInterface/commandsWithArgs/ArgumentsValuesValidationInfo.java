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

import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;

/**
 * Information about {@link com.jetbrains.python.commandInterface.commandsWithArgs.Argument arguments} values validation
 *
 * @author Ilya.Kazakevich
 */
public final class ArgumentsValuesValidationInfo {
  /**
   * Validation with out of any error
   */
  @NotNull
  static final ArgumentsValuesValidationInfo
    NO_ERROR = new ArgumentsValuesValidationInfo(Collections.<Integer, ArgumentValueError>emptyMap(), false);

  private final Map<Integer, ArgumentValueError> myPositionOfErrorArguments = new HashMap<Integer, ArgumentValueError>();
  private final boolean myNotEnoughArguments;

  /**
   * @param positionOfErrorArguments map of [argument_position, its_value_error]
   * @param notEnoughArguments       true if not enough arguments values provided (i.e. some required arg missed)
   */
  ArgumentsValuesValidationInfo(@NotNull final Map<Integer, ArgumentValueError> positionOfErrorArguments,
                                final boolean notEnoughArguments) {
    myPositionOfErrorArguments.putAll(positionOfErrorArguments);
    myNotEnoughArguments = notEnoughArguments;
  }

  /**
   * @return map of [argument_position, its_value_error]
   */
  @NotNull
  Map<Integer, ArgumentValueError> getPositionOfErrorArguments() {
    return Collections.unmodifiableMap(myPositionOfErrorArguments);
  }

  /**
   * @return if not enough argument values provided (i.e. some required arg missed)
   */
  boolean isNotEnoughArguments() {
    return myNotEnoughArguments;
  }

  /**
   * Type of argument value error.
   */
  enum ArgumentValueError {
    /**
     * This argument is redundant
     */
    EXCESS,
    /**
     * Argument has bad value
     */
    BAD_VALUE
  }
}
