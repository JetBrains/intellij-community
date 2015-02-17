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
package com.jetbrains.python.commandLineParser;

import com.jetbrains.python.WordWithPosition;
import org.jetbrains.annotations.NotNull;

/**
 * Part of command line. Known subclasses are {@link CommandLineOption} and {@link CommandLineArgument}
 *
 * @author Ilya.Kazakevich
 * @see CommandLineArgument
 * @see CommandLineOption
 */
public abstract class CommandLinePart {
  @NotNull
  private final WordWithPosition myWord;

  /**
   * @param word word (and its position) this part represents
   */
  protected CommandLinePart(@NotNull final WordWithPosition word) {
    myWord = word;
  }

  /**
   * @return word (and its position) this part represents
   */
  @NotNull
  public final WordWithPosition getWord() {
    return myWord;
  }

  /**
   * @param visitor visitor to accept
   */
  public abstract void accept(@NotNull CommandLinePartVisitor visitor);
}
