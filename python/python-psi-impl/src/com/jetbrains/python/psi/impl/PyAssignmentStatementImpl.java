// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class PyAssignmentStatementImpl extends PyElementImpl implements PyAssignmentStatement {
  private volatile PyExpression @Nullable [] myTargets;

  public PyAssignmentStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentStatement(this);
  }

  @Override
  public PyExpression @NotNull [] getTargets() {
    PyExpression[] result = myTargets;
    if (result == null) {
      myTargets = result = calcTargets(false);
    }
    return result;
  }

  @Override
  public PyExpression @NotNull [] getRawTargets() {
    return calcTargets(true);
  }

  private PyExpression @NotNull [] calcTargets(boolean raw) {
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
    return targets.toArray(PyExpression.EMPTY_ARRAY);
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

  @Override
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
          final PyExpression expression =
            elementGenerator.createExpressionFromText(languageLevel, "(" + rhs_one.getText() + ")[" + counter + "]");
          map.add(Pair.create(tuple_elt, expression));
        }
        catch (IncorrectOperationException e) {
          // not parsed, no problem
        }
        ++counter;
      }
    }
    else if (lhs_tuple != null && rhs_tuple != null) { // multiple both sides: piecewise mapping
      final List<PyExpression> lhsTupleElements = Arrays.asList(lhs_tuple.getElements());
      final List<PyExpression> rhsTupleElements = Arrays.asList(rhs_tuple.getElements());
      final int size = Math.max(lhsTupleElements.size(), rhsTupleElements.size());

      map.addAll(StreamEx.zip(alignToSize(lhsTupleElements, size), alignToSize(rhsTupleElements, size), Pair::create).toList());
    }
  }

  @NotNull
  private static <T> List<T> alignToSize(@NotNull List<T> list, int size) {
    return list.size() == size
           ? list
           : IntStreamEx.range(size).mapToObj(index -> ContainerUtil.getOrElse(list, index, null)).toList();
  }

  @Nullable
  public PsiNamedElement getNamedElement(@NotNull final String the_name) {
    // performance: check simple case first
    PyExpression[] targets = getTargets();
    if (targets.length == 1 && targets[0] instanceof PyTargetExpression target) {
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
