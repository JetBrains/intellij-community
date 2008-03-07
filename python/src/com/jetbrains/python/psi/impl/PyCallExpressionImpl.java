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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 29.05.2005
 * Time: 13:40:29
 * To change this template use File | Settings | File Templates.
 */
public class PyCallExpressionImpl extends PyElementImpl implements PyCallExpression {
  public PyCallExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyCallExpression(this);
  }

  @PsiCached
  public PyReferenceExpression getCalledFunctionReference() {
    return PsiTreeUtil.getChildOfType(this, PyReferenceExpression.class);
  }

  @PsiCached
  public PyArgumentList getArgumentList() {
    return PsiTreeUtil.getChildOfType(this, PyArgumentList.class);
  }

  public void addArgument(PyExpression expression) {
    PyExpression[] arguments = getArgumentList().getArguments();
    try {
      getLanguage().getElementGenerator()
        .insertItemIntoList(getProject(), this, arguments.length == 0 ? null : arguments[arguments.length - 1], expression);
    }
    catch (IncorrectOperationException e1) {
      throw new IllegalArgumentException(e1);
    }
  }

  public PyElement resolveCallee() {
    PyReferenceExpression calleeReference = getCalledFunctionReference();
    return (PyElement) calleeReference.getReference().resolve();
  }

  @Override
  public String toString() {
    return "PyCallExpression: " + getCalledFunctionReference().getReferencedName();
  }

  public PyType getType() {
    PyReferenceExpression callee = getCalledFunctionReference();
    PsiElement target = callee.resolve();
    if (target instanceof PyClass) {
      return new PyClassType((PyClass) target);
    }
    return null;
  }
}
