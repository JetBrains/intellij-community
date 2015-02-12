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
package com.jetbrains.python.commandInterface.command;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * For options, whose argument is based on certain type.
 *
 * @author Ilya.Kazakevich
 * @see com.jetbrains.python.commandInterface.command.OptionArgumentType
 */
public final class OptionTypedArgumentInfo implements OptionArgumentInfo {
  @NotNull
  private final OptionArgumentType myType;

  /**
   * @param type type argument(s) of this option may have
   */
  public OptionTypedArgumentInfo(@NotNull final OptionArgumentType type) {
    myType = type;
  }

  @Override
  public boolean isValid(@NotNull final String value) {
    // We only check integer for now
    if (myType == OptionArgumentType.INTEGER) {
      try {
        // We just parse it to get exception
        //noinspection ResultOfMethodCallIgnored
        Integer.parseInt(value);
      }
      catch (final NumberFormatException ignored) {
        return false;
      }
    }
    return true;
  }

  @Nullable
  @Override
  public List<String> getAvailableValues() {
    return null;
  }
}
