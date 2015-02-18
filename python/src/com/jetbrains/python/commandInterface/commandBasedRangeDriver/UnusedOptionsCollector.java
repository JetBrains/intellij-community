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
package com.jetbrains.python.commandInterface.commandBasedRangeDriver;

import com.intellij.util.containers.HashSet;
import com.jetbrains.python.commandInterface.command.Command;
import com.jetbrains.python.commandInterface.command.Option;
import com.jetbrains.python.commandLineParser.CommandLineArgument;
import com.jetbrains.python.commandLineParser.CommandLineOption;
import com.jetbrains.python.commandLineParser.CommandLinePart;
import com.jetbrains.python.commandLineParser.CommandLinePartVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Collections all used and unused options to report unused options then.
 * It may be used to add unused options to suggestion list {@link #addUnusedOptions(Collection)}
 *
 * @author Ilya.Kazakevich
 */
final class UnusedOptionsCollector implements CommandLinePartVisitor {
  @NotNull
  private final Set<String> myUnusedOptions = new HashSet<String>();
  @NotNull
  private final Command myCommand;

  /**
   * @param command          command to be used
   * @param commandLineParts command line parts
   * @return instance
   */
  static UnusedOptionsCollector create(@NotNull final Command command, @NotNull final Iterable<CommandLinePart> commandLineParts) {
    final UnusedOptionsCollector collector = new UnusedOptionsCollector(command);
    for (final CommandLinePart part : commandLineParts) {
      part.accept(collector);
    }
    return collector;
  }

  private UnusedOptionsCollector(@NotNull final Command command) {
    for (final Option option : command.getOptions()) {
      myUnusedOptions.addAll(option.getAllNames());
    }
    myCommand = command;
  }

  @Override
  public void visitOption(@NotNull final CommandLineOption option) {
    final Option commandOption = CommandBasedRangeInfoDriver.findOptionByName(myCommand, option.getOptionName());
    if (commandOption != null) {
      myUnusedOptions.removeAll(commandOption.getAllNames());
    }
  }

  /**
   * Merges list of unused options and other values provided as argument.
   *
   * @param mainValues values to merge options with. May be null.
   * @return null of no options and no values provided or merged list of values and options. See method usages for more info
   */
  @Nullable
  List<String> addUnusedOptions(@Nullable final Collection<String> mainValues) {
    if (mainValues == null && myUnusedOptions.isEmpty()) {
      return null;
    }
    final List<String> result = new ArrayList<String>(myUnusedOptions);

    if (mainValues != null) {
      result.addAll(mainValues);
    }
    return (result.isEmpty() ? null : result);
  }


  @Override
  public void visitArgument(@NotNull final CommandLineArgument argument) {

  }
}
