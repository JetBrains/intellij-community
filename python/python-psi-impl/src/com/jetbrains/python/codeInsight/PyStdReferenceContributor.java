// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.patterns.PatternCondition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;


@ApiStatus.Internal
public final class PyStdReferenceContributor extends PsiReferenceContributor {
  private static class Holder {
    private static final PatternCondition<PsiElement> IN_OPTIONAL_PARENTHESIS_INSIDE_ASSIGNMENT =
      new PatternCondition<>("in optional parenthesis inside assignment") {
        @Override
        public boolean accepts(@NotNull PsiElement element, ProcessingContext context) {
          PsiElement parent = element.getParent();
          while (parent instanceof PyParenthesizedExpression) {
            parent = parent.getParent();
          }
          return parent instanceof PyAssignmentStatement;
        }
      };
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registerClassAttributeReference(registrar, PyNames.ALL, new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                             @NotNull ProcessingContext context) {
        return new PsiReference[]{new PyDunderAllReference((PyStringLiteralExpression)element)};
      }
    });

    registerClassAttributeReference(registrar, PyNames.SLOTS, new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                             @NotNull ProcessingContext context) {
        return new PsiReference[]{new PyDunderSlotsReference((PyStringLiteralExpression)element)};
      }
    });

    registerClassAttributeReference(registrar, PyNames.MATCH_ARGS, new PsiReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                                                             @NotNull ProcessingContext context) {
        return new PsiReference[]{new PyDunderMatchArgsReference((PyStringLiteralExpression)element)};
      }
    });
  }

  private static void registerClassAttributeReference(PsiReferenceRegistrar registrar,
                                                      final String name,
                                                      final PsiReferenceProvider provider) {
    registrar.registerReferenceProvider(psiElement(PyStringLiteralExpression.class)
                                          .withParent(psiElement(PySequenceExpression.class)
                                                        .with(Holder.IN_OPTIONAL_PARENTHESIS_INSIDE_ASSIGNMENT)
                                                        .inside(true, psiElement(PyAssignmentStatement.class)
                                                          .withFirstChild(psiElement(PyTargetExpression.class).withName(name)))), provider);
  }
}
