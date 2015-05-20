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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.jetbrains.commandInterface.command.Argument;
import com.jetbrains.commandInterface.command.Help;
import com.jetbrains.commandInterface.command.Option;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineArgument;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Ref to be injected in command line argument
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineArgumentReference extends CommandLineElementReference<CommandLineArgument> {
  CommandLineArgumentReference(@NotNull final CommandLineArgument element) {
    super(element);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return null;
  }


  @NotNull
  @Override
  public Object[] getVariants() {
    final LookupWithIndentsBuilder builder = new LookupWithIndentsBuilder();
    final Argument argument = getElement().findRealArgument();
    final Option argumentOption = getElement().findOptionForOptionArgument();
    final Collection<String> argumentValues = (argument != null ? argument.getAvailableValues() : null);

    // priority is used to display args before options
    if (argumentValues != null) {
      for (final String value : argumentValues) {
        final Help help = getElement().findBestHelp();
        final String helpText = (help != null ? help.getHelpString() : null);
        builder.addElement(LookupElementBuilder.create(value).withBoldness(true), helpText, 1);
      }
    }


    final ValidationResult validationResult = getValidationResult();
    if (validationResult == null) {
      return EMPTY_ARRAY;
    }

    if (argumentOption == null) { // If not option argument
      for (final Option option : validationResult.getUnusedOptions()) {
        for (final String value : option.getAllNames()) {
          builder.addElement(LookupElementBuilder.create(value), option.getHelp().getHelpString(), 0);
        }
      }
    }
    return builder.getResult();
  }
}
