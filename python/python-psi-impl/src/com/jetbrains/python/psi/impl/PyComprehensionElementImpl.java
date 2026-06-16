// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Comprehension-like element base, for list comps ang generators.
 */
public abstract class PyComprehensionElementImpl extends PyElementImpl implements PyComprehensionElement {
  public PyComprehensionElementImpl(ASTNode astNode) {
    super(astNode);
  }

  /**
   * Type of a single element produced by the comprehension result expression.
   * For a starred result (PEP 798), e.g. {@code [*it for it in its]}, this is the iterated element type of the
   * unpacked iterable rather than the type of the iterable itself.
   */
  public static @Nullable PyType getResultElementType(@Nullable PyExpression resultExpression, @NotNull TypeEvalContext context) {
    if (resultExpression == null) {
      return null;
    }
    if (resultExpression instanceof PyStarExpression starExpression) {
      final PyExpression iterable = starExpression.getExpression();
      if (iterable == null) {
        return null;
      }
      return PyTargetExpressionImpl.getIterationType(context.getType(iterable), iterable, starExpression, false, context);
    }
    return context.getType(resultExpression);
  }
}
