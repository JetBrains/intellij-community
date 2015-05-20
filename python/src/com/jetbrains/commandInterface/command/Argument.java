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
package com.jetbrains.commandInterface.command;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

// TODO: Support regex validation as well

/**
 * Command argument (positional or option argument)
 * This class represents command argument, not its value.
 *
 * @author Ilya.Kazakevich
 */
public final class Argument {
  /**
   * Argument help user-readable text
   */
  @NotNull
  private final Help myHelpText;
  /**
   * List of values argument may have. Null if any value is possible.
   * Second argument is True if values can <strong>only</strong> be one from these values, or false if any value actually supported
   */
  @Nullable
  private final Pair<List<String>, Boolean> myAvailableValues;
  @Nullable
  private final ArgumentType myType;


  /**
   * @param helpText Argument help user-readable text
   */
  public Argument(@NotNull final Help helpText) {
    this(helpText, null, null);
  }

  /**
   * @param type Argument value type. Null if any type is possible.
   */
  public Argument(@NotNull final ArgumentType type) {
    this(new Help(""), type);
  }

  /**
   * @param helpText Argument help user-readable text
   * @param type     Argument value type. Null if any type is possible.
   */
  public Argument(@NotNull final Help helpText,
                  @Nullable final ArgumentType type) {
    this(helpText, null, type);
  }

  /**
   * @param availableValues List of values argument may have. Null if any value is possible.
   *                        Second argument is True if values can <strong>only</strong> be one from these values, or false if any value actually supported
   */
  public Argument(@Nullable final Pair<List<String>, Boolean> availableValues) {
    this(new Help(""), availableValues, null);
  }

  /**
   * @param helpText        Argument help user-readable text
   * @param availableValues List of values argument may have. Null if any value is possible.
   *                        Second argument is True if values can <strong>only</strong>be one from these values, or false if any value actually supported
   */
  public Argument(@NotNull final Help helpText, @NotNull final Pair<List<String>, Boolean> availableValues) {
    this(helpText, availableValues, null);
  }


  /**
   * @param helpText        Argument help user-readable text
   * @param availableValues List of values argument may have. Null if any value is possible.
   *                        Second argument is True if values can <strong>only</strong> be one from these values, or false if any value actually supported
   * @param type            Argument value type. Null if any type is possible.
   */
  public Argument(@NotNull final Help helpText,
                  @Nullable final Pair<List<String>, Boolean> availableValues,
                  @Nullable final ArgumentType type) {
    myHelpText = helpText;
    myAvailableValues =
      (availableValues == null ? null : Pair.create(Collections.unmodifiableList(availableValues.first), availableValues.second));
    myType = type;
  }

  /**
   * @return Argument help user-readable text
   */
  @NotNull
  public Help getHelp() {
    return myHelpText;
  }

  /**
   * @return List of values argument may have. Null if any value is possible.
   */
  @Nullable
  public List<String> getAvailableValues() {
    return (myAvailableValues == null ? null : Collections.unmodifiableList(myAvailableValues.first));
  }


  /**
   * Validates argument value. Argument tries its best to validate value based on information, provided by constructor.
   *
   * @param value value to check
   * @return true if argument may have this value.
   */
  public boolean isValid(@NotNull final String value) {
    if (!isTypeValid(value)) {
      return false;
    }

    if (myAvailableValues == null || !myAvailableValues.second) { // If no available values or any value is allowed
      return true;
    }
    return myAvailableValues.first.contains(value);
  }

  /**
   * Ensures value conforms type (if known)
   *
   * @param value value to check
   * @return false if type is known and it differs from value
   */
  private boolean isTypeValid(@NotNull final String value) {
    // We only check integer for now
    if (myType == ArgumentType.INTEGER) {
      try {
        // We just getCommandLineInfo it to get exception
        //noinspection ResultOfMethodCallIgnored
        Integer.parseInt(value);
      }
      catch (final NumberFormatException ignored) {
        return false;
      }
    }
    return true;
  }
}
