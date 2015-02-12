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

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Command option
 * @author Ilya.Kazakevich
 */
public final class Option {
  @NotNull
  private final List<String> myLongNames = new ArrayList<String>();
  @NotNull
  private final List<String> myShortNames = new ArrayList<String>();
  @Nullable
  private final Pair<Integer, OptionArgumentInfo> myArgumentAndQuantity;
  @NotNull
  private final String myHelp;

  /**
   *
   * @param argumentAndQuantity if option accepts argument, there should be pair of [argument_quantity, its_type_info]
   * @param help option help
   * @param shortNames option short names
   * @param longNames option long names
   */
  public Option(@Nullable final Pair<Integer, OptionArgumentInfo> argumentAndQuantity,
                @NotNull final String help,
                @NotNull final Collection<String> shortNames,
                @NotNull final Collection<String> longNames) {
    Preconditions.checkArgument(argumentAndQuantity == null || argumentAndQuantity.first > 0, "Illegal args and quantity: " + argumentAndQuantity);
    myArgumentAndQuantity = argumentAndQuantity;
    myShortNames.addAll(shortNames);
    myLongNames.addAll(longNames);
    myHelp = help;
  }

  /**
   * @return Option long names
   */
  @NotNull
  public List<String> getLongNames() {
    return Collections.unmodifiableList(myLongNames);
  }

  /**
   * @return Option short names
   */
  @NotNull
  public List<String> getShortNames() {
    return Collections.unmodifiableList(myShortNames);
  }

  /**
   *
   * @return  if option accepts argument -- pair of [argument_quantity, its_type_info]. Null otherwise.
   */
  @Nullable
  public Pair<Integer, OptionArgumentInfo> getArgumentAndQuantity() {
    return myArgumentAndQuantity;
  }

  /**
   * @return Option help
   */
  @NotNull
  public String getHelp() {
    return myHelp;
  }
}
