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
 * TODO: Redundant?!
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
  public Pair<Boolean, Argument> getArgument(final int argumentPosition) {
    return null;
  }
}
