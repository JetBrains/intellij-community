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

import com.google.common.base.Preconditions;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Command option.
 * It may have some long names (like --foo) and short (like -f), help text and arguments (if not flag option)
 *
 * @author Ilya.Kazakevich
 */
public final class Option {
  @NotNull
  private final List<String> myLongNames = new ArrayList<>();
  @NotNull
  private final List<String> myShortNames = new ArrayList<>();
  @Nullable
  private final Pair<Integer, Argument> myArgumentAndQuantity;
  @NotNull
  private final Help myHelp;

  /**
   * @param argumentAndQuantity if option accepts argument, there should be pair of [argument_quantity, its_type_info]
   * @param help                option help
   * @param shortNames          option short names
   * @param longNames           option long names
   */
  public Option(@Nullable final Pair<Integer, Argument> argumentAndQuantity,
                @NotNull final Help help,
                @NotNull final Collection<String> shortNames,
                @NotNull final Collection<String> longNames) {
    Preconditions
      .checkArgument(argumentAndQuantity == null || argumentAndQuantity.first > 0, "Illegal args and quantity: " + argumentAndQuantity);
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
   * @return all option names (long and short)
   */
  @NotNull
  public List<String> getAllNames() {
    final List<String> result = new ArrayList<>(myLongNames);
    result.addAll(myShortNames);
    return result;
  }

  /**
   * @return Option short names
   */
  @NotNull
  public List<String> getShortNames() {
    return Collections.unmodifiableList(myShortNames);
  }

  // TODO: USe "known arguments info" to prevent copy/paste
  /**
   * @return if option accepts argument -- pair of [argument_quantity, argument]. Null otherwise.
   * Unlike position argument, option argument is <a href="https://docs.python.org/2/library/optparse.html#terminology">always mandatory</a>
   */
  @Nullable
  public Pair<Integer, Argument> getArgumentAndQuantity() {
    return myArgumentAndQuantity;
  }

  /**
   * @return Option help
   */
  @NotNull
  public Help getHelp() {
    return myHelp;
  }
}
