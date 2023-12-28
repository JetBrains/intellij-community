// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;


public class PyTupleExpressionImpl extends PySequenceExpressionImpl implements PyTupleExpression {
  public PyTupleExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTupleExpression(this);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    return PyTupleType.create(this, ContainerUtil.map(getElements(), context::getType));
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    super.deleteChildInternal(child);
    final PyExpression[] children = getElements();
    final PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
    if (children.length == 1 && PyPsiUtils.getNextComma(children[0]) == null ) {
      addAfter(generator.createComma().getPsi(), children[0]);
    }
    else if (children.length == 0 && !(getParent() instanceof PyParenthesizedExpression)) {
      replace(generator.createExpressionFromText(LanguageLevel.forElement(this), "()"));
    }
  }
}
