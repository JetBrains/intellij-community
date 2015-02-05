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

import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.python.commandInterface.commandsWithArgs.ArgumentsValuesValidationInfo.ArgumentValueError;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Case when command has no arguments (for sure!)
 *
 * @author Ilya.Kazakevich
 */
public final class NoArgumentsInfo implements ArgumentsInfo {
  /**
   * Instance to use when command has no arguments
   */
  public static final ArgumentsInfo INSTANCE = new NoArgumentsInfo();

  private NoArgumentsInfo() {
  }

  @Nullable
  @Override
  public Argument getArgument(final int argumentPosition) {
    return null;
  }

  @NotNull
  @Override
  public ArgumentsValuesValidationInfo validateArgumentValues(@NotNull final List<String> argumentValuesToCheck) {
    if (argumentValuesToCheck.isEmpty()) {
      return ArgumentsValuesValidationInfo.NO_ERROR;
    }
    final Map<Integer, ArgumentValueError> errors =
      new HashMap<Integer, ArgumentsValuesValidationInfo.ArgumentValueError>();
    for (final int errorPosition : ContiguousSet.create(Range.closedOpen(0, argumentValuesToCheck.size()), DiscreteDomain.integers())) {
      errors.put(errorPosition, ArgumentValueError.EXCESS);
    }
    return new ArgumentsValuesValidationInfo(errors, false);
  }
}
