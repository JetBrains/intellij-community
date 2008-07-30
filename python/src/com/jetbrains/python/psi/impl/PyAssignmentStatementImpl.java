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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
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
    if (nodes.length > 0) {
      final PyExpression target = (PyExpression) nodes [0].getPsi();
      if (target instanceof PyTupleExpression) {
        return ((PyTupleExpression) target).getElements();
      }
      return new PyExpression[] { target };
    }
    return PyExpression.EMPTY_ARRAY;
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

  protected static List<PyElement> _unfoldParenExprs(PyElement[] targets, List<PyElement> receiver) {
    // NOTE: this proliferation of instanceofs is not very beautiful. Maybe rewrite using a visitor.
    for (PyElement exp : targets) {
      if (exp instanceof PyParenthesizedExpression) {
        final PyParenthesizedExpression parex = (PyParenthesizedExpression)exp;
        PyExpression cont = parex.getContainedExpression();
        if (cont instanceof PyTupleExpression) {
          final PyTupleExpression tupex = (PyTupleExpression)cont;
          _unfoldParenExprs(tupex.getElements(), receiver);
        }
        else receiver.add(exp);
      }
      else receiver.add(exp);
    }
    return receiver;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyExpression[] targets = getTargets();
    return _unfoldParenExprs(targets, new ArrayList<PyElement>(targets.length));
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return true; // a = a+1 resolves 'a' outside itself.
  }
}
