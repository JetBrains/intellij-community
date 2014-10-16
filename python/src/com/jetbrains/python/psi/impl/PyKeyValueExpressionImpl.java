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
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyKeyValueExpressionImpl extends PyElementImpl implements PyKeyValueExpression {
  public PyKeyValueExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final PyType keyType = context.getType(getKey());
    final PyExpression value = getValue();
    PyType valueType = null;
    if (value != null) {
      valueType = context.getType(value);
    }
    return PyTupleType.create(this, new PyType[] {keyType, valueType});
  }

  @NotNull
  public PyExpression getKey() {
    return (PyExpression)getNode().getFirstChildNode().getPsi();
  }

  @Nullable
  public PyExpression getValue() {
    return PsiTreeUtil.getNextSiblingOfType(getKey(), PyExpression.class);
  }
}
