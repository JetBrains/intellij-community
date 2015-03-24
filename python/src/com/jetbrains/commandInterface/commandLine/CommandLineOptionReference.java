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
import com.jetbrains.commandInterface.command.Option;
import com.jetbrains.commandInterface.commandLine.psi.CommandLineOption;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ref to be injected into command line option
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineOptionReference extends CommandLineElementReference<CommandLineOption> {
  CommandLineOptionReference(@NotNull final CommandLineOption element) {
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

    final ValidationResult validationResult = getValidationResult();
    if (validationResult == null) {
      return EMPTY_ARRAY;
    }

    for (final Option option : validationResult.getUnusedOptions()) {
      // Suggest long options for -- and short for -
      final List<String> names = getElement().isLong() ? option.getLongNames() : option.getShortNames();
      for (final String optionName : names) {
        builder.addElement(LookupElementBuilder.create(optionName), option.getHelp().getHelpString());
      }
    }

    return builder.getResult();
  }
}
