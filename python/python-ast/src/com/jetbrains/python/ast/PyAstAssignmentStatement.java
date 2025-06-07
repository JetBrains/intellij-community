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
    // cast for convenience
    PyAstSequenceExpression lhs_tuple = null;
    PyAstExpression lhs_one = null;
    if (PyPsiUtilsCore.flattenParens(lhs) instanceof PyAstTupleExpression<?> tupleExpr) lhs_tuple = tupleExpr;
    else if (lhs != null) lhs_one = lhs;

    PyAstSequenceExpression rhs_tuple = null;
    PyAstExpression rhs_one = null;

    if (PyPsiUtilsCore.flattenParens(rhs) instanceof PyAstTupleExpression<?> tupleExpr) rhs_tuple = tupleExpr;
    else if (rhs != null) rhs_one = rhs;
    //
    if (lhs_one != null) { // single LHS, single RHS (direct mapping) or multiple RHS (packing)
      map.add(Pair.create(lhs_one, rhs));
    }
    else if (lhs_tuple != null && rhs_one != null) { // multiple LHS, single RHS: unpacking
      // PY-2648, PY-2649
      PyAstElementGenerator elementGenerator = PyAstElementGenerator.getInstance(rhs_one.getProject());
      final LanguageLevel languageLevel = LanguageLevel.forElement(lhs);
      int counter = 0;
      for (PyAstExpression tuple_elt : lhs_tuple.getElements()) {
        try {
          final PyAstExpression expression =
            elementGenerator.createExpressionFromText(languageLevel, "(" + rhs_one.getText() + ")[" + counter + "]");
          mapToValues(tuple_elt, expression, map);
        }
        catch (IncorrectOperationException e) {
          // not parsed, no problem
        }
        ++counter;
      }
    }
    else if (lhs_tuple != null && rhs_tuple != null) { // multiple both sides: piecewise mapping
      final List<PyAstExpression> lhsTupleElements = Arrays.asList(lhs_tuple.getElements());
      final List<PyAstExpression> rhsTupleElements = Arrays.asList(rhs_tuple.getElements());
      final int size = Math.max(lhsTupleElements.size(), rhsTupleElements.size());
      for (int index = 0; index < size; index++) {
        mapToValues(ContainerUtil.getOrElse(lhsTupleElements, index, null),
                    ContainerUtil.getOrElse(rhsTupleElements, index, null), map);
      }
    }
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
