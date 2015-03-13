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
package com.jetbrains.commandInterface.gnuCommandLine;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.jetbrains.commandInterface.gnuCommandLine.psi.*;
import com.jetbrains.commandInterface.command.Option;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Ref to be injected in command line argument
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
    final ValidationResult validationResult = getValidationResult();
    if (validationResult == null) {
      return EMPTY_ARRAY;
    }
    final Collection<LookupElement> result = new ArrayList<LookupElement>();
    final Collection<String> argumentValues = validationResult.getPossibleArgumentValues(getElement());

    // priority is used to display args before options
    if (argumentValues != null) {
      for (final String value : argumentValues) {
        result.add(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(value).withBoldness(true), 1));
      }
    }


    if (!validationResult.isOptionArgument(getElement())) {
      for (final Option option : validationResult.getUnusedOptions()) {
        for (final String value : option.getAllNames()) {
          result.add(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(value).withTailText(" :" + option.getHelp()), 0));
        }
      }
    }
    return ArrayUtil.toObjectArray(result);
  }
}
