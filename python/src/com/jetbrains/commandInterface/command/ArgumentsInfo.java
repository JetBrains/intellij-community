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
package com.jetbrains.commandInterface.command;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * Information about command {@link Argument arguments} and their value
 * validation.
 * Check optparse manual, package info and {@link Argument}
 * manual for more info about arguments.
 *
 * @author Ilya.Kazakevich
 */
public interface ArgumentsInfo {
  /**
   * Returns argument by its position. It also returns hint whether argument is required or not.
   *
   * @param argumentPosition argument position
   * @return null if no argument value is available at this position.
   * Returns argument otherwise. Boolean here should tell you if argument is required (command is invalid with out of it) or optional (
   * it is acceptible, but command can work with our of it)
   */
  @Nullable
  Pair<Boolean, Argument> getArgument(int argumentPosition);
}
