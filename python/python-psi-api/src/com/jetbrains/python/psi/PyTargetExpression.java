// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.*;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public interface PyTargetExpression extends PyQualifiedExpression, PsiNamedElement, PsiNameIdentifierOwner, PyDocStringOwner,
                                            PyQualifiedNameOwner, PyReferenceOwner, StubBasedPsiElement<PyTargetExpressionStub>,
                                            PyPossibleClassMember, PyTypeCommentOwner, PyAnnotationOwner {
  PyTargetExpression[] EMPTY_ARRAY = new PyTargetExpression[0];

  /**
   * Find the value that maps to this target expression in an enclosing assignment expression.
   * Does not work with other expressions (e.g. if the target is in a 'for' loop).
   *
   * Operates at the AST level.
   *
   * @return the expression assigned to target via an enclosing assignment expression, or null.
   */
  @Nullable
  PyExpression findAssignedValue();

  /**
   * Multi-resolves the value that maps to this target expression in an enclosing assignment expression.
   *
   * This method does not access AST if underlying PSI is stub based and the context doesn't allow switching to AST.
   *
   * @param resolveContext resolve context
   * @return the resolved assigned values or an empty list.
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  List<PsiElement> multiResolveAssignedValue(@NotNull PyResolveContext resolveContext);

  /**
   * Returns the qualified name (if there is any) assigned to the expression.
   *
   * This method does not access AST if underlying PSI is stub based.
   */
  @Nullable
  QualifiedName getAssignedQName();

  /**
   * If the value assigned to the target expression is a call, returns the (unqualified and unresolved) name of the
   * callee. Otherwise, returns null.
   *
   * @return the name of the callee or null if the assigned value is not a call.
   */
  @Nullable
  QualifiedName getCalleeName();

  @Override
  @NotNull
  PsiReference getReference();

  /**
   * Checks if target has assigned value.
   *
   * This method does not access AST if underlying PSI is stub based.
   *
   * @return true if target has assigned expression, false otherwise (e.g. in type declaration statement).
   */
  default boolean hasAssignedValue() {
    return true;
  }
}
