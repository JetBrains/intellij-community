/*
 *  Copyright 2005 Pythonid Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS"; BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.FP;
import com.jetbrains.python.toolbox.RepeatIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 13:05:38
 * To change this template use File | Settings | File Templates.
 */
public class PyAssignmentStatementImpl extends PyElementImpl implements PyAssignmentStatement {
  public PyAssignmentStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAssignmentStatement(this);
  }

  public PyExpression[] getTargets() {
    final ASTNode[] nodes = getNode().getChildren(PyElementTypes.EXPRESSIONS);
    PyExpression[] psi_nodes = new PyExpression[nodes.length];
    for (int i = 0; i < nodes.length; i += 1) psi_nodes[i] = (PyExpression)nodes[i].getPsi();
    List<PyExpression> candidates = PyUtil.flattenedParens(psi_nodes); // put all possible tuples to one level
    List<PyExpression> targets = new ArrayList<PyExpression>();
    for (PyExpression expr : candidates) { // only filter out targets
      if (expr instanceof PyTargetExpression) {
        targets.add(expr);
      }
    }
    return targets.toArray(new PyExpression[targets.size()]);
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
    List<Pair<PyExpression, PyExpression>> ret = new SmartList<Pair<PyExpression, PyExpression>>();
    if (!PsiTreeUtil.hasErrorElements(this)) { // no parse errors
      PyExpression[] constituents = PsiTreeUtil.getChildrenOfType(this, PyExpression.class); // "a = b = c" -> [a, b, c]
      if (constituents != null && constituents.length > 1) {
        PyExpression rhs = constituents[constituents.length - 1]; // last
        List<PyExpression> lhses = new ArrayList<PyExpression>(constituents.length - 1);
        for (int i = 0; i < constituents.length - 1; i += 1) lhses.add(constituents[i]); // copy all but last; most often it's one element.
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

  private static void mapToValues(PyExpression lhs, PyExpression rhs, List<Pair<PyExpression, PyExpression>> map) {
    // cast for convenience
    PyTupleExpression lhs_tuple = null;
    PyExpression lhs_one = null;
    if (lhs instanceof PyTupleExpression) lhs_tuple = (PyTupleExpression)lhs;
    else if (lhs != null) lhs_one = lhs;
    
    PyTupleExpression rhs_tuple = null;
    PyExpression rhs_one = null;
    if (rhs instanceof PyTupleExpression) rhs_tuple = (PyTupleExpression)rhs;
    else if (rhs != null) rhs_one = rhs;
    //
    if (lhs_one != null) { // single LHS, single RHS (direct mapping) or multiple RHS (packing)
       map.add(new Pair<PyExpression, PyExpression>(lhs_one, rhs));
    }
    else if (lhs_tuple != null && rhs_one != null) { // multiple LHS, single RHS: unpacking
      //for (PyExpression tuple_elt : lhs_tuple.getElements()) map.add(new Pair<PyExpression, PyExpression>(tuple_elt, rhs_one));
      map.addAll(FP.zipList(lhs_tuple, new RepeatIterable<PyExpression>(rhs_one)));
    }
    else if (lhs_tuple != null && rhs_tuple != null) { // multiple both sides: piecewise mapping
      map.addAll(FP.zipList(lhs_tuple, rhs_tuple, null, null));
    }
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    // a reference can never be resolved within the same assignment statement
    if (lastParent != null) {
      return true;
    }

    if (PsiTreeUtil.getParentOfType(this, PyFunction.class) == null && PsiTreeUtil.getParentOfType(place, PyFunction.class) != null) {

      // The scope of names defined in a class block is limited to the class block;
      // it does not extend to the code blocks of methods.
      if (PsiTreeUtil.getParentOfType(this, PyClass.class) != null) {
        return true;
      }
      if (PsiTreeUtil.getParentOfType(place, PyGlobalStatement.class) == null) {
        return true;
      }
    }

    for (PyExpression expression : getTargets()) {
      if (!expression.processDeclarations(processor, substitutor, lastParent, place)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyElement[] targets = getTargets();
    // return _unfoldParenExprs(targets, new ArrayList<PyElement>(targets.length));
    return PyUtil.flattenedParens(targets);
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return true; // a = a+1 resolves 'a' outside itself.
  }
}
