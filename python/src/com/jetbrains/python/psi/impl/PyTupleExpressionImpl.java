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
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

/**
 * @author yole
 */
public class PyTupleExpressionImpl extends PySequenceExpressionImpl implements PyTupleExpression {
  public PyTupleExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTupleExpression(this);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyExpression[] elements = getElements();
    final PyType[] types = new PyType[elements.length];
    for (int i = 0; i < types.length; i++) {
      types [i] = context.getType(elements [i]);
    }
    return PyTupleType.create(this, types);
  }

  public Iterator<PyExpression> iterator() {
    return Arrays.asList(getElements()).iterator();
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
