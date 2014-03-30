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
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

/**
 * @author yole
 */
public class PyTupleExpressionImpl extends PyElementImpl implements PyTupleExpression {
  public PyTupleExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyTupleExpression(this);
  }

  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
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
    return Arrays.<PyExpression>asList(getElements()).iterator();
  }
}
