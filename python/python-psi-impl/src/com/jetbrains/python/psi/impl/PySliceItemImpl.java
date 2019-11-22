// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySliceItem;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PySliceItemImpl extends PyElementImpl implements PySliceItem {
  public PySliceItemImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  @Nullable
  public PyExpression getLowerBound() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
  }

  @Override
  @Nullable
  public PyExpression getUpperBound() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
  }

  @Override
  @Nullable
  public PyExpression getStride() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 2);
  }
}
