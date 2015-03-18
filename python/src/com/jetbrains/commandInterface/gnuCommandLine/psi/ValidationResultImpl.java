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
package com.jetbrains.commandInterface.gnuCommandLine.psi;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.commandInterface.gnuCommandLine.ValidationResult;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.command.Command;
import com.jetbrains.commandInterface.command.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Validation result provider and holder implemented as visitor.
 * @author Ilya.Kazakevich
 */
final class ValidationResultImpl extends CommandLineVisitor implements ValidationResult {
  /**
   * List of options with names
   */
  @NotNull
  private final HashMap<String, Option> myOptions = new HashMap<String, Option>();
  /**
   * We always need command to validate args
   */
  @NotNull
  private final Command myCommand;
  /**
   * Number of next positional argument. I.e. will be 3 for "my_arg arg_1 arg_2"
   */
  private int myCurrentPositionArgument;
  /**
   * If next arg is supposed to be option arg, then option and number of expected args stored here.
   * Null stored otherwise.
   */
  @Nullable
  private Pair<Option, Integer> myCurrentOptionAndArgsLeft;
  /**
   * List of elements whose values are known to be bad
   */
  @NotNull
  private final Collection<PsiElement> myBadValues = new ArrayList<PsiElement>();
  /**
   * List of elements which is known to be excess
   */
  @NotNull
  private final Collection<CommandLineArgument> myExcessArguments = new ArrayList<CommandLineArgument>();
  /**
   * Possible values for argument
   */
  @NotNull
  private final Map<CommandLineArgument, List<String>> myPossibleValues = new HashMap<CommandLineArgument, List<String>>();
  /**
   * List of arguments known to be option arguments
   */
  @NotNull
  private final Collection<CommandLineArgument> myOptionArguments = new ArrayList<CommandLineArgument>();

  private ValidationResultImpl(@NotNull final Command command) {
    for (final Option option : command.getOptions()) {
      for (final String optionName : option.getAllNames()) {
        myOptions.put(optionName, option);
      }
    }
    myCommand = command;
  }

  @Override
  public boolean isBadValue(@NotNull final PsiElement element) {
    return myBadValues.contains(element);
  }

  @Override
  public boolean isExcessArgument(@NotNull final CommandLineArgument argument) {
    return myExcessArguments.contains(argument);
  }

  @Override
  @NotNull
  public Collection<Option> getUnusedOptions() {
    return myOptions.values();
  }


  @Override
  public boolean isOptionArgument(@NotNull final CommandLineArgument argument) {
    return myOptionArguments.contains(argument);
  }

  @Override
  @Nullable
  public Collection<String> getPossibleArgumentValues(@NotNull final CommandLineArgument argument) {
    return myPossibleValues.get(argument);
  }

  /**
   * Creates validation result by file
   * @param file file to validate
   * @return validation result or null if file has no command or command is unknown
   */
  @Nullable
  static ValidationResult create(final CommandLineFile file) {
    final Command command = file.findRealCommand();
    if (command == null) {
      return null;
    }
    final ValidationResultImpl validationLayout = new ValidationResultImpl(command);
    file.acceptChildren(validationLayout);
    return validationLayout;
  }

  @Override
  public void visitArgument(@NotNull final CommandLineArgument o) {
    super.visitArgument(o);
    if (myCurrentOptionAndArgsLeft != null) {
      processOptionArgument(o);
      return;
    }

    // Process as positional
    processPositionalArgument(o);
  }

  private void processPositionalArgument(@NotNull final CommandLineArgument o) {
    final Pair<Boolean, Argument> argumentPair = myCommand.getArgumentsInfo().getArgument(myCurrentPositionArgument++);
    if (argumentPair == null) {
      myExcessArguments.add(o);
    }
    else {
      processArgument(o, argumentPair.second);
    }
  }

  private void processOptionArgument(@NotNull final CommandLineArgument o) {
    assert myCurrentOptionAndArgsLeft != null: "Method can't be called if no current option exist";
    if (myCurrentOptionAndArgsLeft.second > 0) {
      myCurrentOptionAndArgsLeft = Pair.create(myCurrentOptionAndArgsLeft.first, myCurrentOptionAndArgsLeft.second - 1);
      final Pair<Integer, Argument> argumentAndQuantity = myCurrentOptionAndArgsLeft.first.getArgumentAndQuantity();
      // TODO: Use class instead of pair to prevent such a stupid checks
      assert argumentAndQuantity != null: "Option has arguments left but no argument info";
      final Argument argumentInfo = argumentAndQuantity.getSecond();
      processArgument(o, argumentInfo);

      myOptionArguments.add(o);
    }
    else if (myCurrentOptionAndArgsLeft.second == 0) {
      myCurrentOptionAndArgsLeft = null;
      myExcessArguments.add(o);
    }
  }

  private void processArgument(@NotNull final CommandLineArgument o, final Argument argumentInfo) {
    final List<String> availableValues = argumentInfo.getAvailableValues();
    if (availableValues != null) {
      myPossibleValues.put(o, availableValues);
    }
    if (!argumentInfo.isValid(o.getText())) {
      myBadValues.add(o);
    }
  }


  @Override
  public void visitWhiteSpace(final PsiWhiteSpace space) {
    super.visitWhiteSpace(space);
    // -aSHORT_OPT_ARGUMENT, but -a NEW_POSITION_ARGUMENT, so whitespace makes sense
    if (myCurrentOptionAndArgsLeft != null && myCurrentOptionAndArgsLeft.second == 0) {
      myCurrentOptionAndArgsLeft = null;
    }
  }

  @Override
  public void visitOption(@NotNull final CommandLineOption o) {
    super.visitOption(o);
    if (myOptions.containsKey(o.getOptionName())) {
      // Remove from list of available options
      final Option option = myOptions.remove(o.getOptionName());
      for (final String optionName : option.getAllNames()) {
        myOptions.remove(optionName);
      }

      final Pair<Integer, Argument> argumentAndQuantity = option.getArgumentAndQuantity();
      if (argumentAndQuantity != null) {
        myCurrentOptionAndArgsLeft = Pair.create(option, argumentAndQuantity.first);
      }
      else {
        myCurrentOptionAndArgsLeft = new Pair<Option, Integer>(option, 0);
      }
    }
    else {
      myBadValues.add(o); //No such option available
    }
  }

  @Nullable
  @Override
  public Pair<Boolean, Argument> getNextArg() {
    return myCommand.getArgumentsInfo().getArgument(myCurrentPositionArgument);
  }
}
