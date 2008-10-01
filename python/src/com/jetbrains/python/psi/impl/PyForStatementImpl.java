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
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyStatementList;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 21:21:12
 * To change this template use File | Settings | File Templates.
 */
public class PyForStatementImpl extends PyElementImpl implements PyForStatement {
  public PyForStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyForStatement(this);
  }

  @NotNull
  public PyStatementList getStatementList() {
    return childToPsiNotNull(PyElementTypes.STATEMENT_LISTS, 0);
  }

  @Nullable
  public PyStatementList getElseStatementList() {
    return childToPsi(PyElementTypes.STATEMENT_LISTS, 1);
  }

  @Nullable
  public PyExpression getTargetExpression() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 0);
  }

  @Nullable
  public PyExpression getLoopExpression() {
    return childToPsi(PyElementTypes.EXPRESSIONS, 1);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState substitutor,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final PyExpression target = getTargetExpression();
    if (target != null && target != lastParent && !target.processDeclarations(processor, substitutor, null, place)) {
      return false;
    }

    final PyStatementList statementList = getStatementList();
    if (statementList != lastParent && !statementList.processDeclarations(processor, substitutor, null, place)) {
      return false;
    }
    PyStatementList elseList = getElseStatementList();
    if (elseList != null && elseList != lastParent) {
      return elseList.processDeclarations(processor, substitutor, null, place);
    }
    return true;
  }

  @NotNull
  public Iterable<PyElement> iterateNames() {
    PyExpression tgt = getTargetExpression();
    if (tgt instanceof PyReferenceExpression) return new SingleIterable<PyElement>(tgt);
    else {
      return PyUtil.flattenedParens(new PyElement[]{tgt});
    }
  }

  public PyElement getElementNamed(final String the_name) {
    return IterHelper.findName(iterateNames(), the_name);
  }

  public boolean mustResolveOutside() {
    return false; 
  }
}
