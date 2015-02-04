/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// TODO: Support regex validation as well

/**
 * Command <strong>positional, not named</strong> argument (not option!).
 * This class represents command argument, not its value.
 *
 *
 * @author Ilya.Kazakevich
 */
public final class Argument {
  /**
   * Argument help user-readable text
   */
  @NotNull
  private final String myHelpText;
  /**
   * List of values argument may have. Null if any value is possible.
   */
  @Nullable
  private final List<String> myAvailableValues;


  /**
   * @param helpText Argument help user-readable text
   */
  public Argument(@NotNull final String helpText) {
    this(helpText, null);
  }

  /**
   * @param helpText        Argument help user-readable text
   * @param availableValues List of values argument may have. Null if any value is possible.
   */
  public Argument(@NotNull final String helpText, @Nullable final List<String> availableValues) {
    myHelpText = helpText;
    myAvailableValues = (availableValues == null ? null : new ArrayList<String>(availableValues));
  }

  /**
   * @return Argument help user-readable text
   */
  @NotNull
  public String getHelpText() {
    return myHelpText;
  }

  /**
   * @return List of values argument may have. Null if any value is possible.
   */
  @Nullable
  public List<String> getAvailableValues() {
    return (myAvailableValues == null ? null : Collections.unmodifiableList(myAvailableValues));
  }
}
