/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.google.common.base.Predicates;
import com.intellij.psi.PsiPolyVariantReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

public interface PyReferenceExpression extends PyQualifiedExpression, PyReferenceOwner {
  PyReferenceExpression[] EMPTY_ARRAY = new PyReferenceExpression[0];

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
  @NotNull
  default List<QualifiedRatedResolveResult> multiFollowAssignmentsChain(@NotNull PyResolveContext resolveContext) {
    return multiFollowAssignmentsChain(resolveContext, Predicates.alwaysTrue());
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
