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
package com.jetbrains.python.psi;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes an assignment statement.
 */
public interface PyAssignmentStatement extends PyStatement, PyNamedElementContainer, PyAnnotationOwner {

  /**
   * @return the left-hand side of the statement; each item may consist of many elements.
   */
  @NotNull
  PyExpression[] getTargets();

  /**
   * Return all expressions which are considered assignment targets (to the left of the last = sign in the statement).
   * Doesn't unpack tuples, parentheses or anything.
   *
   * @return the list of assignment target expressions
   */
  @NotNull
  PyExpression[] getRawTargets();


  /**
   * @return right-hand side of the statement; may as well consist of many elements.
   */
  @Nullable
  PyExpression getAssignedValue();

  /**
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
  @NotNull
  List<Pair<PyExpression, PyExpression>> getTargetsToValuesMapping();

  @Nullable
  PyExpression getLeftHandSideExpression();

  boolean isAssignmentTo(@NotNull String name);

}
