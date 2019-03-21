// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.InitialPatternCondition;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.codeInsight.completion.pathFilters.PyPathFilter;
import com.jetbrains.python.patterns.PyElementPattern;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PythonPathReferenceContributor extends PsiReferenceContributor {
  public static final ExtensionPointName<PyPathFilter> EP = new ExtensionPointName<>("Pythonid.stringPathFilter");
  private final PyPathFilter myFilter;

  public PythonPathReferenceContributor() {
    // Combine all filters through "or" in a one filter.
    myFilter = EP.getExtensionList().stream().reduce(
      expr -> false,
      (f1, f2) -> ((PyStringLiteralExpression expr) -> f1.test(expr) || f2.test(expr)));
  }

  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar
      .registerReferenceProvider(matcher(myFilter),
                                 new PythonPathReferenceProvider());
  }

  public static PyElementPattern.Capture<PyStringLiteralExpression> matcher(PyPathFilter filter) {
    return new PyElementPattern.Capture<>(new InitialPatternCondition<PyStringLiteralExpression>(PyStringLiteralExpression.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        final PyStringLiteralExpression expr = (PyStringLiteralExpression)o;

        return filter.test(expr);
      }
    });
  }
}
