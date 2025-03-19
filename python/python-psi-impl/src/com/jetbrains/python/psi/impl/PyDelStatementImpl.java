// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;


public class PyDelStatementImpl extends PyElementImpl implements PyDelStatement, PsiListLikeElement {
  public PyDelStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDelStatement(this);
  }

  @Override
  public PyExpression @NotNull [] getTargets() {
    return childrenToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return Arrays.asList(getTargets());
  }
}
