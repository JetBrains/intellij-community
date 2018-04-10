// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyYieldExpressionImpl extends PyElementImpl implements PyYieldExpression {
  public PyYieldExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyYieldExpression(this);
  }

  @Override
  public PyExpression getExpression() {
    final PyExpression[] expressions = PsiTreeUtil.getChildrenOfType(this, PyExpression.class);
    return (expressions != null && expressions.length > 0) ? expressions[0] : null;
  }

  @Override
  public boolean isDelegating() {
    return getNode().findChildByType(PyTokenTypes.FROM_KEYWORD) != null;
  }

  @Override
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression e = getExpression();
    final PyType type = e != null ? context.getType(e) : null;
    if (isDelegating()) {
      final Ref<PyType> generatorElementType = PyTypingTypeProvider.coroutineOrGeneratorElementType(type);
      return generatorElementType == null ? PyNoneType.INSTANCE : generatorElementType.get();
    }
    return type;
  }
}
