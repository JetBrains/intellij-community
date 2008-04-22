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
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

public class PyListLiteralExpressionImpl extends PyElementImpl implements PyListLiteralExpression {
  public PyListLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyListLiteralExpression(this);
  }

  @PsiCached
  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PyElementTypes.EXPRESSIONS, PyExpression.EMPTY_ARRAY);
  }

  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    PyUtil.ensureWritable(this);
    checkPyExpression(psiElement);
    PyExpression element = (PyExpression)psiElement;
    PyExpression[] els = getElements();
    PyExpression lastArg = els.length == 0 ? null : els[els.length - 1];
    return getLanguage().getElementGenerator().insertItemIntoList(getProject(), this, lastArg, element);
  }

  private static void checkPyExpression(PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PyExpression)) {
      throw new IncorrectOperationException("Element must be PyExpression: " + psiElement);
    }
  }

  public PsiElement addAfter(@NotNull PsiElement psiElement, PsiElement afterThis) throws IncorrectOperationException {
    PyUtil.ensureWritable(this);
    checkPyExpression(psiElement);
    checkPyExpression(afterThis);
    return getLanguage().getElementGenerator().insertItemIntoList(getProject(), this, (PyExpression)afterThis, (PyExpression)psiElement);
  }

  public PsiElement addBefore(@NotNull PsiElement psiElement, PsiElement beforeThis) throws IncorrectOperationException {
    PyUtil.ensureWritable(this);
    checkPyExpression(psiElement);
    return getLanguage().getElementGenerator().insertItemIntoList(getProject(), this, null, (PyExpression)psiElement);
  }

  protected void deletePyChild(PyBaseElementImpl element) throws IncorrectOperationException {
    PyUtil.ensureWritable(this);
    if (element instanceof PyExpression) {
      PyExpression expression = (PyExpression)element;
      ASTNode node = getNode();
      ASTNode exprNode = expression.getNode();
      ASTNode next = PyPsiUtils.getNextComma(exprNode);
      ASTNode prev = PyPsiUtils.getPrevComma(exprNode);
      node.removeChild(exprNode);
      if (next != null) {
        node.removeChild(next);
      }
      else if (prev != null) {
        node.removeChild(prev);
      }
    }
    else {
      super.deletePyChild(element);
    }
  }

  @NotNull
  public PyType getType() {
    PyClass cls = PyBuiltinCache.getInstance(getProject()).getListClass();
    assert(cls != null);
    return new PyClassType(cls);
  }
}
