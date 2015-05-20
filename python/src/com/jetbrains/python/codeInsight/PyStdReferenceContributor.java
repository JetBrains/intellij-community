/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.patterns.PatternCondition;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class PyStdReferenceContributor extends PsiReferenceContributor {
  public static final PatternCondition<PsiElement> IN_OPTIONAL_PARENTHESIS_INSIDE_ASSIGNMENT =
    new PatternCondition<PsiElement>("in optional parenthesis inside assignment") {
      @Override
      public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
        PsiElement parent = element.getParent();
        while (parent instanceof PyParenthesizedExpression) {
          parent = parent.getParent();
        }
        return parent instanceof PyAssignmentStatement;
      }
    };

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerClassAttributeReference(registrar, PyNames.ALL, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                   @NotNull ProcessingContext context) {
        return new PsiReference[]{new PyDunderAllReference((PyStringLiteralExpression)element)};
      }
    });

    registerClassAttributeReference(registrar, PyNames.SLOTS, new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element,
                                                   @NotNull ProcessingContext context) {
        return new PsiReference[]{new PyDunderSlotsReference((PyStringLiteralExpression)element)};
      }
    });
  }

  private static void registerClassAttributeReference(PsiReferenceRegistrar registrar,
                                                      final String name,
                                                      final PsiReferenceProvider provider) {
    registrar.registerReferenceProvider(psiElement(PyStringLiteralExpression.class)
                                          .withParent(psiElement(PySequenceExpression.class)
                                                        .with(IN_OPTIONAL_PARENTHESIS_INSIDE_ASSIGNMENT)
                                                        .inside(true, psiElement(PyAssignmentStatement.class)
                                                          .withFirstChild(psiElement(PyTargetExpression.class).withName(name)))), provider);
  }
}
