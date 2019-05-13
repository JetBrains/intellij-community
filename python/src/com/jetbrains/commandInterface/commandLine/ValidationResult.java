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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.command.Option;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineArgument;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * TODO: This is not validation result, but actually parsing result. So, it has to be renamed
 * Result of command line validation
 *
 * @author Ilya.Kazakevich
 */
public interface ValidationResult {
  /**
   * @param element element to check
   * @return true if element is known to have bad/illegal value
   */
  boolean isBadValue(@NotNull PsiElement element);

  /**
   * @param argument element to check
   * @return true if argument is excess
   */
  boolean isExcessArgument(@NotNull CommandLineArgument argument);

  /**
   * @return list of allowed options unused by user
   */
  @NotNull
  Collection<Option> getUnusedOptions();

  /**
   * Returns option for argument if argument is option argument. Returns null otherwise (for positional arguments etc)
   * @param argument argument to check
   * @return option or null if no option associated with this argument
   */
  @Nullable
  Option getOptionForOptionArgument(@NotNull CommandLineArgument argument);


  /**
   * Returns real command argument by psi element
   * @param commandLineArgument psi element
   * @return real argument (positional or optional) or null if can't be find
   */
  @Nullable
  Argument getArgument(@NotNull CommandLineArgument commandLineArgument);

  /**
   * Returns real option argument by psi element
   * @param option psi option
   * @return real option or null if can't be find
   */
  @Nullable
  Option getOption(@NotNull CommandLineOption option);

  /**
   * @return next argument for command in format [is_required, argument] or null if no argument allowed here
   */
  @Nullable
  Pair<Boolean, Argument> getNextArg();
}
