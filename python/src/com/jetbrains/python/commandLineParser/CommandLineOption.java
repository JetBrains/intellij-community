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
import org.jetbrains.annotations.Nullable;

/**
 * Option. Each option has name and may also have attached argument like "--long-option=attached_arg" or "-sATTACHED_ARG"
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineOption extends CommandLinePart {

  @NotNull
  private final String myOptionName;

  @Nullable
  private final WordWithPosition myAttachedArgument;

  /**
   * @param option           option (text and position)
   * @param optionName       option name (like "--foo")
   * @param attachedArgument option  attached argument (like --foo=ATTACHED_ARG)
   */
  public CommandLineOption(@NotNull final WordWithPosition option,
                           @NotNull final String optionName,
                           @Nullable final WordWithPosition attachedArgument) {
    super(option);
    myOptionName = optionName;
    myAttachedArgument = attachedArgument;
  }


  /**
   * @return option name (like "--foo")
   */
  @NotNull
  public String getOptionName() {
    return myOptionName;
  }

  /**
   * @return option  attached argument (like --foo=ATTACHED_ARG)
   */
  @Nullable
  public WordWithPosition getAttachedArgument() {
    return myAttachedArgument;
  }

  @Override
  public void accept(@NotNull final CommandLinePartVisitor visitor) {
    visitor.visitOption(this);
  }
}
