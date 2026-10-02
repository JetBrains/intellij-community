// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight;

import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;


@ApiStatus.Internal
public final class PythonFormattedStringReferenceContributor extends PsiReferenceContributor {
  static final class Holder {
    private static final PsiElementPattern.Capture<PyStringLiteralExpression> PERCENT_STRING_PATTERN =
      psiElement(PyStringLiteralExpression.class).beforeLeaf(psiElement().withText("%")).withParent(PyBinaryExpression.class);
    static final PsiElementPattern.Capture<PyStringLiteralExpression> FORMAT_STRING_PATTERN =
      psiElement(PyStringLiteralExpression.class)
        .withParent(psiElement(PyReferenceExpression.class)
                      .with(new PatternCondition<>("isFormatFunction") {

                        @Override
                        public boolean accepts(@NotNull PyReferenceExpression expression, ProcessingContext context) {
                          String expressionName = expression.getName();
                          return expressionName != null && expressionName.equals("format");
                        }
                      }))
        .withSuperParent(2, PyCallExpression.class);
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {

    registrar.registerReferenceProvider(psiElement().andOr(Holder.PERCENT_STRING_PATTERN, Holder.FORMAT_STRING_PATTERN),
                                        new PythonFormattedStringReferenceProvider());
  }
}
