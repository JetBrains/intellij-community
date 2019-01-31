// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author yole
 */
public class PyConditionalExpressionImpl extends PyElementImpl implements PyConditionalExpression {
  public PyConditionalExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression truePart = getTruePart();
    final PyExpression falsePart = getFalsePart();
    if (truePart == null || falsePart == null) {
      return null;
    }
    return PyUnionType.union(context.getType(truePart), context.getType(falsePart));
  }

  @Override
  public PyExpression getTruePart() {
    final List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
    return expressions.get(0);
  }

  @Override
  public PyExpression getCondition() {
    final List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
    return expressions.size() > 1 ? expressions.get(1) : null;
  }

  @Override
  public PyExpression getFalsePart() {
    final List<PyExpression> expressions = PsiTreeUtil.getChildrenOfTypeAsList(this, PyExpression.class);
    return expressions.size() == 3 ? expressions.get(2) : null;
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyConditionalExpression(this);
  }
}
