/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
