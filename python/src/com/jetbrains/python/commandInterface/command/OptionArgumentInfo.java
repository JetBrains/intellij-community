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
 * Information about option argument
 *
 * @author Ilya.Kazakevich
 */
public interface OptionArgumentInfo {
  /**
   * Validates argument value
   *
   * @param value value to validate
   * @return true if valid
   */
  boolean isValid(@NotNull String value);

  /**
   * @return list of available values (if argument is based on list of choices), or null if any value is accepted (but should be validated)
   */
  @Nullable
  List<String> getAvailableValues();
}
