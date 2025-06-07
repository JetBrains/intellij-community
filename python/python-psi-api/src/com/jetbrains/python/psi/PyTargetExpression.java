// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.psi.*;
import com.jetbrains.python.ast.PyAstTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public interface PyTargetExpression extends PyAstTargetExpression, PyQualifiedExpression, PsiNamedElement, PsiNameIdentifierOwner,
                                            PsiExternalReferenceHost, PyDocStringOwner,
                                            PyQualifiedNameOwner, PyReferenceOwner, StubBasedPsiElement<PyTargetExpressionStub>,
                                            PyPossibleClassMember, PyTypeCommentOwner, PyAnnotationOwner {
  PyTargetExpression[] EMPTY_ARRAY = new PyTargetExpression[0];

  @Override
  default @Nullable PyAnnotation getAnnotation() {
    return (PyAnnotation)PyAstTargetExpression.super.getAnnotation();
  }

  @Override
  default @Nullable PyExpression getQualifier() {
    return (PyExpression)PyAstTargetExpression.super.getQualifier();
  }

  @Override
  default @Nullable PyClass getContainingClass() {
    return (PyClass)PyAstTargetExpression.super.getContainingClass();
  }

  @Override
  default @Nullable PyStringLiteralExpression getDocStringExpression() {
    return (PyStringLiteralExpression)PyAstTargetExpression.super.getDocStringExpression();
  }

  /**
   * Find the value that maps to this target expression in an enclosing assignment expression.
   * Does not work with other expressions (e.g. if the target is in a 'for' loop).
   * <p>
   * Operates at the AST level.
   *
   * @return the expression assigned to target via an enclosing assignment expression, or null.
   */
  @Override
  default @Nullable PyExpression findAssignedValue() {
    return (PyExpression)PyAstTargetExpression.super.findAssignedValue();
  }

  /**
   * Multi-resolves the value that maps to this target expression in an enclosing assignment expression.
   * <p>
   * This method does not access AST if underlying PSI is stub based and the context doesn't allow switching to AST.
   *
   * @param resolveContext resolve context
   * @return the resolved assigned values or an empty list.
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  List<PsiElement> multiResolveAssignedValue(@NotNull PyResolveContext resolveContext);

  @Override
  @NotNull
  PsiReference getReference();
}
