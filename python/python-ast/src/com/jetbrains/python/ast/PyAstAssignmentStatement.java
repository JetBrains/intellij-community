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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAstElementGenerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildByClass;

/**
 * Describes an assignment statement.
 */
@ApiStatus.Experimental
public interface PyAstAssignmentStatement extends PyAstStatement, PyAstNamedElementContainer, PyAstAnnotationOwner {

  @ApiStatus.Internal
  static <T extends PyAstExpression> T @NotNull [] calcTargets(@NotNull PyAstAssignmentStatement statement,
                                                               boolean raw,
                                                               T @NotNull [] result) {
    final ASTNode[] eqSigns = statement.getNode().getChildren(TokenSet.create(PyTokenTypes.EQ));
    if (eqSigns.length == 0) {
      return result;
    }
    final ASTNode lastEq = eqSigns[eqSigns.length - 1];
    List<PyAstExpression> candidates = new ArrayList<>();
    ASTNode node = statement.getNode().getFirstChildNode();
    while (node != null && node != lastEq) {
      final PsiElement psi = node.getPsi();
      if (psi instanceof PyAstExpression expression) {
        if (raw) {
          candidates.add(expression);
        }
        else {
          addCandidate(candidates, expression);
        }
      }
      node = node.getTreeNext();
    }
    List<PyAstExpression> targets = new ArrayList<>();
    for (PyAstExpression expr : candidates) { // only filter out targets
      if (raw ||
          expr instanceof PyAstTargetExpression ||
          expr instanceof PyAstReferenceExpression ||
          expr instanceof PyAstSubscriptionExpression) {
        targets.add(expr);
      }
    }
    return targets.toArray(result);
  }

  private static void addCandidate(List<PyAstExpression> candidates, PyAstExpression psi) {
    if (psi instanceof PyAstParenthesizedExpression parenthesizedExpression) {
      addCandidate(candidates, parenthesizedExpression.getContainedExpression());
    }
    else if (psi instanceof PyAstSequenceExpression sequenceExpression) {
      for (PyAstExpression pyExpression : sequenceExpression.getElements()) {
        addCandidate(candidates, pyExpression);
      }
    }
    else if (psi instanceof PyAstStarExpression starExpression) {
      final PyAstExpression expression = starExpression.getExpression();
      if (expression != null) {
        addCandidate(candidates, expression);
      }
    }
    else {
      candidates.add(psi);
    }
  }

  @Override
  default @Nullable PyAstAnnotation getAnnotation() {
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
  default @Nullable PyAstExpression getAssignedValue() {
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
   * List literals on the left-hand side are unpacked like tuples: "{@code [a, b] = 1, 2}" yields [(a,1), (b,2)].
   * A starred target absorbs an arbitrary-length slice and is not mapped to a single value:
   * "{@code a, *b, c = 1, 2, 3, 4}" yields [(a,1), (c,4)].
   * <br/>
   * If RHS and LHS are mis-balanced, certain target or value expressions may be null.
   * If source is severely incorrect, the returned mapping is empty.
   * @return a list of [target, value] pairs; either part of a pair may be null, but not both.
   */
  default @NotNull List<? extends Pair<? extends PyAstExpression, ? extends PyAstExpression>> getTargetsToValuesMapping() {
    List<Pair<PyAstExpression, PyAstExpression>> ret = new SmartList<>();
    if (!PsiTreeUtil.hasErrorElements(this)) { // no parse errors
      PyAstExpression[] constituents = PsiTreeUtil.getChildrenOfType(this, PyAstExpression.class); // "a = b = c" -> [a, b, c]
      if (constituents != null && constituents.length > 1) {
        int lastIndex = constituents.length - 1;
        PyAstExpression rhs = constituents[lastIndex];
        for (int i = 0; i < lastIndex; i++) {
          mapToValues(constituents[i], rhs, ret);
        }
      }
    }
    return ret;
  }

  private static void mapToValues(@Nullable PyAstExpression lhs,
                                  @Nullable PyAstExpression rhs,
                                  List<Pair<PyAstExpression, PyAstExpression>> map) {
    PyAstSequenceExpression lhs_tuple = null;
    PyAstExpression lhs_one = null;
    final PyAstExpression flattenedLhs = PyPsiUtilsCore.flattenParens(lhs);
    if (flattenedLhs instanceof PyAstTupleExpression<?> tupleExpr) lhs_tuple = tupleExpr;
    else if (flattenedLhs instanceof PyAstListLiteralExpression listExpr) lhs_tuple = listExpr; // [a, b] = ...
    else if (lhs != null) lhs_one = lhs;

    PyAstSequenceExpression rhs_tuple = null;
    PyAstExpression rhs_one = null;

    if (PyPsiUtilsCore.flattenParens(rhs) instanceof PyAstTupleExpression<?> tupleExpr) rhs_tuple = tupleExpr;
    else if (rhs != null) rhs_one = rhs;
    if (lhs_one != null) { // single LHS, single RHS (direct mapping) or multiple RHS (packing)
      map.add(Pair.create(lhs_one, rhs));
    }
    else if (lhs_tuple != null && rhs_one != null) { // multiple LHS, single RHS: unpacking
      // PY-2648, PY-2649
      mapTargetsToSubscriptions(lhs_tuple.getElements(), rhs_one, map);
    }
    else if (lhs_tuple != null && rhs_tuple != null) { // multiple both sides: piecewise mapping
      mapTargetsToValues(lhs_tuple.getElements(), rhs_tuple.getElements(), map);
    }
  }

  /**
   * Unpacks a sequence of targets from a single RHS expression by generating subscription expressions.
   * A starred target absorbs an arbitrary-length slice, so targets following it are addressed with
   * negative indices counted from the end: {@code a, *b, c = expr} maps {@code a} to {@code (expr)[0]}
   * and {@code c} to {@code (expr)[-1]}, while the starred target itself is not mapped to a single value.
   */
  private static void mapTargetsToSubscriptions(PyAstExpression @NotNull [] targets,
                                                @NotNull PyAstExpression rhs,
                                                List<Pair<PyAstExpression, PyAstExpression>> map) {
    final PyAstElementGenerator elementGenerator = PyAstElementGenerator.getInstance(rhs.getProject());
    final LanguageLevel languageLevel = LanguageLevel.forElement(rhs);
    final int starIndex = indexOfStar(targets);
    for (int i = 0; i < targets.length; i++) {
      if (targets[i] instanceof PyAstStarExpression) continue; // starred target maps to a slice, not a single value
      final int index = starIndex < 0 || i < starIndex ? i : i - targets.length;
      try {
        final PyAstExpression expression =
          elementGenerator.createExpressionFromText(languageLevel, "(" + rhs.getText() + ")[" + index + "]");
        mapToValues(targets[i], expression, map);
      }
      catch (IncorrectOperationException e) {
        // not parsed, no problem
      }
    }
  }

  /**
   * Maps a sequence of targets to a sequence of values element-wise.
   * A single starred target absorbs the middle, so targets before it are aligned from the start and
   * targets after it from the end; the starred target itself is not mapped to a single value.
   */
  private static void mapTargetsToValues(PyAstExpression @NotNull [] targets,
                                         PyAstExpression @NotNull [] values,
                                         List<Pair<PyAstExpression, PyAstExpression>> map) {
    final int targetStar = indexOfStar(targets);
    final int valueStar = indexOfStar(values);
    if (targetStar < 0 && valueStar < 0) {
      final List<PyAstExpression> targetList = Arrays.asList(targets);
      final List<PyAstExpression> valueList = Arrays.asList(values);
      final int size = Math.max(targetList.size(), valueList.size());
      for (int index = 0; index < size; index++) {
        mapToValues(ContainerUtil.getOrElse(targetList, index, null),
                    ContainerUtil.getOrElse(valueList, index, null), map);
      }
      return;
    }
    // Align the leading run up to the first starred element on either side.
    final int front = Math.min(targetStar < 0 ? targets.length : targetStar,
                               valueStar < 0 ? values.length : valueStar);
    for (int i = 0; i < front; i++) {
      mapToValues(targets[i], values[i], map);
    }
    // Align the trailing run from the end, up to the last starred element on either side.
    final int back = Math.min(targetStar < 0 ? targets.length : targets.length - targetStar - 1,
                              valueStar < 0 ? values.length : values.length - valueStar - 1);
    for (int k = back; k > 0; k--) {
      final int targetIndex = targets.length - k;
      final int valueIndex = values.length - k;
      if (targetIndex < front || valueIndex < front) break; // exhausted; avoid overlapping the leading run
      mapToValues(targets[targetIndex], values[valueIndex], map);
    }
    // A starred element absorbs the remaining middle and is not mapped to a single value.
  }

  private static int indexOfStar(PyAstExpression @NotNull [] elements) {
    for (int i = 0; i < elements.length; i++) {
      if (elements[i] instanceof PyAstStarExpression) return i;
    }
    return -1;
  }

  default @Nullable PyAstExpression getLeftHandSideExpression() {
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
  default @NotNull List<PsiNamedElement> getNamedElements() {
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

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentStatement(this);
  }

}
