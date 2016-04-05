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
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;


public class PythonFormattedStringReferenceContributor extends PsiReferenceContributor {
  public static final PsiElementPattern.Capture<PyStringLiteralExpression> PERCENT_STRING_PATTERN =
    psiElement(PyStringLiteralExpression.class).beforeLeaf(psiElement().withText("%")).withParent(PyBinaryExpression.class);
  public static final PsiElementPattern.Capture<PyStringLiteralExpression> FORMAT_STRING_PATTERN =
    psiElement(PyStringLiteralExpression.class)
      .withParent(psiElement(PyReferenceExpression.class)
                                  .with(new PatternCondition<PyReferenceExpression>("isFormatFunction") {

                                    @Override
                                    public boolean accepts(@NotNull PyReferenceExpression expression, ProcessingContext context) {
                                      String expressionName = expression.getName();
                                      return expressionName != null && expressionName.equals("format");
                                    }
                                  }))
      .withSuperParent(2, PyCallExpression.class);

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {

    registrar.registerReferenceProvider(psiElement().andOr(PERCENT_STRING_PATTERN, FORMAT_STRING_PATTERN), 
                                        new PythonFormattedStringReferenceProvider());
  }
}
