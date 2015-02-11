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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * For options, whose argument is based on list of choices
 * @author Ilya.Kazakevich
 */
public final class OptionChoiceBasedArgumentInfo implements OptionArgumentInfo {
  @NotNull
  private final List<String> myChoices = new ArrayList<String>();

  /**
   * @param choices available choices
   */
  public OptionChoiceBasedArgumentInfo(@NotNull final Collection<String> choices) {
    myChoices.addAll(choices);
  }

  @Override
  public boolean isValid(@NotNull final String value) {
    return myChoices.contains(value);
  }

  @Nullable
  @Override
  public List<String> getAvailableValues() {
    return Collections.unmodifiableList(myChoices);
  }
}
