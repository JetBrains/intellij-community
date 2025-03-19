// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.ast.PyAstReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public interface PyReferenceExpression extends PyAstReferenceExpression, PyQualifiedExpression, PyReferenceOwner, PsiExternalReferenceHost {
  PyReferenceExpression[] EMPTY_ARRAY = new PyReferenceExpression[0];

  @Override
  default @Nullable PyExpression getQualifier() {
    return (PyExpression)PyAstReferenceExpression.super.getQualifier();
  }

  /**
   * Goes through a chain of assignment statements until a non-assignment expression is encountered.
   * Starts at this, expecting it to resolve to a target of an assignment.
   * <i>Note: currently limited to non-branching definite assignments.</i>
   *
   * @param resolveContext resolve context
   * @return the value that is assigned to this element via a chain of definite assignments, or an empty resolve result.
   * <i>Note: will return null if the assignment chain ends in a target of a non-assignment statement such as 'for'.</i>
   * @see PyReferenceExpression#multiFollowAssignmentsChain(PyResolveContext)
   */
  @NotNull
  QualifiedResolveResult followAssignmentsChain(@NotNull PyResolveContext resolveContext);

  /**
   * Goes through a chain of assignment statements until a non-assignment expression is encountered.
   * Starts at this, expecting it to resolve to a target of an assignment.
   *
   * @param resolveContext resolve context
   * @return the values that could be assigned to this element via a chain of assignments, or an empty list.
   * <i>Note: the returned list does not contain null values.</i>
   */
  default @NotNull List<QualifiedRatedResolveResult> multiFollowAssignmentsChain(@NotNull PyResolveContext resolveContext) {
    return multiFollowAssignmentsChain(resolveContext, __->true);
  }

  /**
   * Goes through a chain of assignment statements until a non-assignment expression is encountered.
   * Starts at this, expecting it to resolve to a target of an assignment.
   *
   * @param resolveContext resolve context
   * @param follow predicate to test if target should be followed
   * @return the values that could be assigned to this element via a chain of assignments, or an empty list.
   * <i>Note: the returned list does not contain null values.</i>
   */
  @NotNull
  List<QualifiedRatedResolveResult> multiFollowAssignmentsChain(@NotNull PyResolveContext resolveContext,
                                                                @NotNull Predicate<? super PyTargetExpression> follow);

  @Override
  @NotNull
  PsiPolyVariantReference getReference();
}
