/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyListLiteralExpressionImpl extends PyElementImpl implements PyListLiteralExpression {
  public PyListLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyListLiteralExpression(this);
  }

  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }

  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    checkPyExpression(psiElement);
    PyExpression element = (PyExpression)psiElement;
    PyExpression[] els = getElements();
    PyExpression lastArg = els.length == 0 ? null : els[els.length - 1];
    return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, lastArg, element);
  }

  private static void checkPyExpression(PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PyExpression)) {
      throw new IncorrectOperationException("Element must be PyExpression: " + psiElement);
    }
  }

  public PsiElement addAfter(@NotNull PsiElement psiElement, PsiElement afterThis) throws IncorrectOperationException {
    checkPyExpression(psiElement);
    checkPyExpression(afterThis);
    return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, (PyExpression)afterThis, (PyExpression)psiElement);
  }

  public PsiElement addBefore(@NotNull PsiElement psiElement, PsiElement beforeThis) throws IncorrectOperationException {
    checkPyExpression(psiElement);
    return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, null, (PyExpression)psiElement);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (child.getPsi() instanceof PyExpression) {
      PyExpression expression = (PyExpression)child.getPsi();
      ASTNode node = getNode();
      ASTNode exprNode = expression.getNode();
      ASTNode next = PyPsiUtils.getNextComma(exprNode);
      ASTNode prev = PyPsiUtils.getPrevComma(exprNode);
      super.deleteChildInternal(child);
      if (next != null) {
        deleteChildInternal(next);
      }
      else if (prev != null) {
        deleteChildInternal(prev);
      }
    }
    else {
      super.deleteChildInternal(child);
    }
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyBuiltinCache.createLiteralCollectionType(this, "list");
  }
}
