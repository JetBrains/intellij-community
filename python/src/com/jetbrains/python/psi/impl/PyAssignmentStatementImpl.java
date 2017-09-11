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
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.FP;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author yole
 */
public class PyAssignmentStatementImpl extends PyElementImpl implements PyAssignmentStatement {
  @Nullable private volatile PyExpression[] myTargets;

  public PyAssignmentStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentStatement(this);
  }

  @NotNull
  public PyExpression[] getTargets() {
    PyExpression[] result = myTargets;
    if (result == null) {
      myTargets = result = calcTargets(false);
    }
    return result;
  }

  @NotNull
  @Override
  public PyExpression[] getRawTargets() {
    return calcTargets(true);
  }

  @NotNull
  private PyExpression[] calcTargets(boolean raw) {
    final ASTNode[] eqSigns = getNode().getChildren(TokenSet.create(PyTokenTypes.EQ));
    if (eqSigns.length == 0) {
      return PyExpression.EMPTY_ARRAY;
    }
    final ASTNode lastEq = eqSigns[eqSigns.length - 1];
    List<PyExpression> candidates = new ArrayList<>();
    ASTNode node = getNode().getFirstChildNode();
    while (node != null && node != lastEq) {
      final PsiElement psi = node.getPsi();
      if (psi instanceof PyExpression) {
        if (raw) {
          candidates.add((PyExpression) psi);
        }
        else {
          addCandidate(candidates, (PyExpression)psi);
        }
      }
      node = node.getTreeNext();
    }
    List<PyExpression> targets = new ArrayList<>();
    for (PyExpression expr : candidates) { // only filter out targets
      if (raw ||
          expr instanceof PyTargetExpression ||
          expr instanceof PyReferenceExpression ||
          expr instanceof PySubscriptionExpression ||
          expr instanceof PySliceExpression) {
        targets.add(expr);
      }
    }
    return targets.toArray(new PyExpression[targets.size()]);
  }

  @Nullable
  @Override
  public PyAnnotation getAnnotation() {
    return findChildByClass(PyAnnotation.class);
  }

  @Nullable
  @Override
  public String getAnnotationValue() {
    return getAnnotationContentFromPsi(this);
  }

  private static void addCandidate(List<PyExpression> candidates, PyExpression psi) {
    if (psi instanceof PyParenthesizedExpression) {
      addCandidate(candidates, ((PyParenthesizedExpression)psi).getContainedExpression());
    }
    else if (psi instanceof PySequenceExpression) {
      final PyExpression[] pyExpressions = ((PySequenceExpression)psi).getElements();
      for (PyExpression pyExpression : pyExpressions) {
        addCandidate(candidates, pyExpression);
      }
    }
    else if (psi instanceof PyStarExpression) {
      final PyExpression expression = ((PyStarExpression)psi).getExpression();
      if (expression != null) {
        addCandidate(candidates, expression);
      }
    }
    else {
      candidates.add(psi);
    }
  }

  /**
   * @return rightmost expression in statement, which is supposedly the assigned value, or null.
   */
  @Nullable
  public PyExpression getAssignedValue() {
    PsiElement child = getLastChild();
    while (child != null && !(child instanceof PyExpression)) {
      if (child instanceof PsiErrorElement) return null; // incomplete assignment operator can't be analyzed properly, bail out.
      child = child.getPrevSibling();
    }
    return (PyExpression)child;
  }

  @NotNull
  public List<Pair<PyExpression, PyExpression>> getTargetsToValuesMapping() {
    List<Pair<PyExpression, PyExpression>> ret = new SmartList<>();
    if (!PsiTreeUtil.hasErrorElements(this)) { // no parse errors
      PyExpression[] constituents = PsiTreeUtil.getChildrenOfType(this, PyExpression.class); // "a = b = c" -> [a, b, c]
      if (constituents != null && constituents.length > 1) {
        PyExpression rhs = constituents[constituents.length - 1]; // last
        List<PyExpression> lhses = Lists.newArrayList(constituents);
        if (lhses.size()>0) lhses.remove(lhses.size()-1); // copy all but last; most often it's one element.
        for (PyExpression lhs : lhses) mapToValues(lhs, rhs, ret);
      }
    }
    return ret;
  }

  @Nullable
  public PyExpression getLeftHandSideExpression() {
    PsiElement child = getFirstChild();
    while (child != null && !(child instanceof PyExpression)) {
      if (child instanceof PsiErrorElement) return null; // incomplete assignment operator can't be analyzed properly, bail out.
      child = child.getPrevSibling();
    }
    return (PyExpression)child;
  }

  @Override
  public boolean isAssignmentTo(@NotNull String name) {
    PyExpression lhs = getLeftHandSideExpression();
    return lhs instanceof PyTargetExpression && name.equals(lhs.getName());
  }

  private static void mapToValues(PyExpression lhs, PyExpression rhs, List<Pair<PyExpression, PyExpression>> map) {
    // cast for convenience
    PySequenceExpression lhs_tuple = null;
    PyExpression lhs_one = null;
    if (lhs instanceof PySequenceExpression) lhs_tuple = (PySequenceExpression)lhs;
    else if (lhs != null) lhs_one = lhs;
    
    PySequenceExpression rhs_tuple = null;
    PyExpression rhs_one = null;
    if (rhs instanceof PyParenthesizedExpression) {
      PyExpression exp = ((PyParenthesizedExpression)rhs).getContainedExpression();
      if (exp instanceof PyTupleExpression)
        rhs_tuple = (PySequenceExpression)exp;
      else
        rhs_one = rhs;
    }
    else if (rhs instanceof PySequenceExpression) rhs_tuple = (PySequenceExpression)rhs;
    else if (rhs != null) rhs_one = rhs;
    //
    if (lhs_one != null) { // single LHS, single RHS (direct mapping) or multiple RHS (packing)
       map.add(Pair.create(lhs_one, rhs));
    }
    else if (lhs_tuple != null && rhs_one != null) { // multiple LHS, single RHS: unpacking
      // PY-2648, PY-2649
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(rhs_one.getProject());
      final LanguageLevel languageLevel = LanguageLevel.forElement(lhs);
      int counter = 0;
      for (PyExpression tuple_elt : lhs_tuple.getElements()) {
        try {
          final PyExpression expression = elementGenerator.createExpressionFromText(languageLevel, rhs_one.getText() + "[" + counter + "]");
          map.add(Pair.create(tuple_elt, expression));
        }
        catch (IncorrectOperationException e) {
          // not parsed, no problem
        }
        ++counter;
      }
      //  map.addAll(FP.zipList(Arrays.asList(lhs_tuple.getElements()), new RepeatIterable<PyExpression>(rhs_one)));
    }
    else if (lhs_tuple != null && rhs_tuple != null) { // multiple both sides: piecewise mapping
      map.addAll(FP.zipList(Arrays.asList(lhs_tuple.getElements()), Arrays.asList(rhs_tuple.getElements()), null, null));
    }
  }

  @NotNull
  public List<PsiNamedElement> getNamedElements() {
    final List<PyExpression> expressions = PyUtil.flattenedParensAndStars(getTargets());
    List<PsiNamedElement> result = new ArrayList<>();
    for (PyExpression expression : expressions) {
      if (expression instanceof PyQualifiedExpression && ((PyQualifiedExpression)expression).isQualified()) {
        continue;
      }
      if (expression instanceof PsiNamedElement) {
        result.add((PsiNamedElement)expression);
      }
    }
    return result;

  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    // performance: check simple case first
    PyExpression[] targets = getTargets();
    if (targets.length == 1 && targets[0] instanceof PyTargetExpression) {
      PyTargetExpression target = (PyTargetExpression)targets[0];
      return !target.isQualified() && the_name.equals(target.getName()) ? target : null;
    }
    return PyUtil.IterHelper.findName(getNamedElements(), the_name);
  }

  @Override
  public void subtreeChanged() {
    super.subtreeChanged();
    myTargets = null;
  }
}
