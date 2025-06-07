// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.ast.PyAstAssignmentStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes an assignment statement.
 */
public interface PyAssignmentStatement extends PyAstAssignmentStatement, PyStatement, PyNamedElementContainer, PyAnnotationOwner {

  @Override
  default @Nullable PyAnnotation getAnnotation() {
    return (PyAnnotation)PyAstAssignmentStatement.super.getAnnotation();
  }

  /**
   * @return the left-hand side of the statement; each item may consist of many elements.
   */
  @Override
  PyExpression @NotNull [] getTargets();

  /**
   * Return all expressions which are considered assignment targets (to the left of the last = sign in the statement).
   * Doesn't unpack tuples, parentheses or anything.
   *
   * @return the array of assignment target expressions
   */
  @Override
  PyExpression @NotNull [] getRawTargets();


  /**
   * @return right-hand side of the statement; may as well consist of many elements.
   */
  @Override
  default @Nullable PyExpression getAssignedValue() {
    return (PyExpression)PyAstAssignmentStatement.super.getAssignedValue();
  }

  /*
   * Applies a visitor to every element of left-hand side. Tuple elements are flattened down to their most nested
   * parts. E.g. if the target is <tt>a, b[1], (c(2).d, e.f)</tt>, then expressions
   * <tt>a</tt>, <tt>b[1]</tt>, <tt>c(2).d</tt>, <tt>e.f</tt> will be visited.
   * Order of visiting is not guaranteed.
   * @param visitor its {@link PyElementVisitor#visitPyExpression} method will be called for each elementary target expression
   */
  //void visitElementaryTargets(PyElementVisitor visitor);


  /**
   * Maps target expressions to assigned values, unpacking tuple expressions.
   * For "{@code a, (b, c) = 1, (2, 'foo')}" the result will be [(a,1), (b:2), (c:'foo')].
   * <br/>
   * If there's a number of LHS targets, the RHS expression is mapped to every target.
   * For "{@code a = b = c = 1}" the result will be [(a,1), (b,1), (c,1)].
   * <br/>
   * Elements of tuples and tuples themselves may get interspersed in complex mappings.
   * For "{@code a = b,c = 1,2}" the result will be [(a,(1,2)), (b,1), (c,2)].
   * <br/>
   * If RHS and LHS are mis-balanced, certain target or value expressions may be null.
   * If source is severely incorrect, the returned mapping is empty.
   * @return a list of [target, value] pairs; either part of a pair may be null, but not both.
   */
  @Override
  default @NotNull List<Pair<PyExpression, PyExpression>> getTargetsToValuesMapping() {
    //noinspection unchecked
    return (List<Pair<PyExpression, PyExpression>>)PyAstAssignmentStatement.super.getTargetsToValuesMapping();
  }

  @Override
  default @Nullable PyExpression getLeftHandSideExpression() {
    return (PyExpression)PyAstAssignmentStatement.super.getLeftHandSideExpression();
  }

}
