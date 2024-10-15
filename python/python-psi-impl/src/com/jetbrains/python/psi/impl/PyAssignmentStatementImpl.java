// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      myTargets = result = PyAstAssignmentStatement.calcTargets(this, false, PyExpression.EMPTY_ARRAY);
    }
    return result;
  }

  @Override
  public PyExpression @NotNull [] getRawTargets() {
    return PyAstAssignmentStatement.calcTargets(this, true, PyExpression.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public List<Pair<PyExpression, PyExpression>> getTargetsToValuesMapping() {
    List<Pair<PyExpression, PyExpression>> ret = new SmartList<>();
    if (!PsiTreeUtil.hasErrorElements(this)) { // no parse errors
      PyExpression[] constituents = PsiTreeUtil.getChildrenOfType(this, PyExpression.class); // "a = b = c" -> [a, b, c]
      if (constituents != null && constituents.length > 1) {
        int lastIndex = constituents.length - 1;
        PyExpression rhs = constituents[lastIndex];
        for (int i = 0; i < lastIndex; i++) {
          mapToValues(constituents[i], rhs, ret);
        }
      }
    }
    return ret;
  }

  private static void mapToValues(@Nullable PyExpression lhs, @Nullable PyExpression rhs, List<Pair<PyExpression, PyExpression>> map) {
    // cast for convenience
    PySequenceExpression lhs_tuple = null;
    PyExpression lhs_one = null;
    if (PyPsiUtils.flattenParens(lhs) instanceof PyTupleExpression tupleExpr) lhs_tuple = tupleExpr;
    else if (lhs != null) lhs_one = lhs;

    PySequenceExpression rhs_tuple = null;
    PyExpression rhs_one = null;

    if (PyPsiUtils.flattenParens(rhs) instanceof PyTupleExpression tupleExpr) rhs_tuple = tupleExpr;
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
          mapToValues(tuple_elt, expression, map);
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
      for (int index = 0; index < size; index++) {
        mapToValues(ContainerUtil.getOrElse(lhsTupleElements, index, null),
                    ContainerUtil.getOrElse(rhsTupleElements, index, null), map);
      }
    }
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
