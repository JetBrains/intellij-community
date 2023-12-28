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
package com.jetbrains.python.ast;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiNamedElement;
import com.jetbrains.python.ast.impl.PyUtilCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

/**
 * Describes an assignment statement.
 */
@ApiStatus.Experimental
public interface PyAstAssignmentStatement extends PyAstStatement, PyAstNamedElementContainer, PyAstAnnotationOwner {

  @Nullable
  @Override
  default PyAstAnnotation getAnnotation() {
    return findChildByClass(this, PyAstAnnotation.class);
  }

  /**
   * @return the left-hand side of the statement; each item may consist of many elements.
   */
  PyAstExpression @NotNull [] getTargets();

  /**
   * Return all expressions which are considered assignment targets (to the left of the last = sign in the statement).
   * Doesn't unpack tuples, parentheses or anything.
   *
   * @return the array of assignment target expressions
   */
  PyAstExpression @NotNull [] getRawTargets();

  /**
   * @return rightmost expression in statement, which is supposedly the assigned value, or null.
   */
  @Nullable
  default PyAstExpression getAssignedValue() {
    PsiElement child = getLastChild();
    while (child != null && !(child instanceof PyAstExpression)) {
      if (child instanceof PsiErrorElement) return null; // incomplete assignment operator can't be analyzed properly, bail out.
      child = child.getPrevSibling();
    }
    return (PyAstExpression)child;
  }

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
  List<? extends Pair<? extends PyAstExpression, ? extends PyAstExpression>> getTargetsToValuesMapping();

  @Nullable
  default PyAstExpression getLeftHandSideExpression() {
    PsiElement child = getFirstChild();
    while (child != null && !(child instanceof PyAstExpression)) {
      if (child instanceof PsiErrorElement) return null; // incomplete assignment operator can't be analyzed properly, bail out.
      child = child.getPrevSibling();
    }
    return (PyAstExpression)child;
  }

  default boolean isAssignmentTo(@NotNull String name) {
    PyAstExpression lhs = getLeftHandSideExpression();
    return lhs instanceof PyAstTargetExpression && name.equals(lhs.getName());
  }

  @Override
  @NotNull
  default List<PsiNamedElement> getNamedElements() {
    final List<PyAstExpression> expressions = PyUtilCore.flattenedParensAndStars(getTargets());
    List<PsiNamedElement> result = new ArrayList<>();
    for (PyAstExpression expression : expressions) {
      if (expression instanceof PyAstQualifiedExpression && ((PyAstQualifiedExpression)expression).isQualified()) {
        continue;
      }
      if (expression instanceof PsiNamedElement) {
        result.add((PsiNamedElement)expression);
      }
    }
    return result;

  }

}
