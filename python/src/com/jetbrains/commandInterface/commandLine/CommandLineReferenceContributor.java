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

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.jetbrains.commandInterface.commandLine.psi.*;
import org.jetbrains.annotations.NotNull;

/**
 * Injects references to command-line parts
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineReferenceContributor extends PsiReferenceContributor {
  private static final ReferenceProvider REFERENCE_PROVIDER = new ReferenceProvider();

  @Override
  public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(CommandLineElement.class), REFERENCE_PROVIDER);
  }


  private static class ReferenceProvider extends PsiReferenceProvider {
    @NotNull
    @Override
    public final PsiReference[] getReferencesByElement(@NotNull final PsiElement element,
                                                       @NotNull final ProcessingContext context) {
      if (element instanceof CommandLineCommand) {
        return new PsiReference[]{new CommandLineCommandReference((CommandLineCommand)element)};
      }
      if (element instanceof CommandLineArgument) {
        return new PsiReference[]{new CommandLineArgumentReference((CommandLineArgument)element)};
      }
      if (element instanceof CommandLineOption) {
        return new PsiReference[]{new CommandLineOptionReference((CommandLineOption)element)};
      }
      return PsiReference.EMPTY_ARRAY;
    }
  }
}

