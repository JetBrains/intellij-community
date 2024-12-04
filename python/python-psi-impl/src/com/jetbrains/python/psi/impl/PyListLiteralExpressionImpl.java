// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyCollectionTypeUtil;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

public class PyListLiteralExpressionImpl extends PySequenceExpressionImpl implements PyListLiteralExpression {
  public PyListLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyListLiteralExpression(this);
  }

  @Override
  public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
    checkPyExpression(psiElement);
    PyExpression element = (PyExpression)psiElement;
    PyExpression[] els = getElements();
    PyExpression lastArg = els.length == 0 ? null : els[els.length - 1];
    return PyElementGenerator.getInstance(getProject()).insertItemIntoListRemoveRedundantCommas(this, lastArg, element);
  }

  private static void checkPyExpression(PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PyExpression)) {
      throw new IncorrectOperationException("Element must be PyExpression: " + psiElement);
    }
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement psiElement, PsiElement afterThis) throws IncorrectOperationException {
    checkPyExpression(psiElement);
    checkPyExpression(afterThis);
    return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, (PyExpression)afterThis, (PyExpression)psiElement);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement psiElement, PsiElement beforeThis) throws IncorrectOperationException {
    checkPyExpression(psiElement);
    return PyElementGenerator.getInstance(getProject()).insertItemIntoList(this, null, (PyExpression)psiElement);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyCollectionTypeUtil.getListLiteralType(this, context);
  }
}
