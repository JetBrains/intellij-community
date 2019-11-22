// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public abstract class PySequenceExpressionImpl extends PyElementImpl implements PySequenceExpression {
  public PySequenceExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ArrayUtil.contains(child.getPsi(), getElements())) {
      PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
    }
    super.deleteChildInternal(child);
  }

  @Override
  @NotNull
  public PyExpression[] getElements() {
    return childrenToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), PyExpression.EMPTY_ARRAY);
  }

  @Override
  public boolean isEmpty() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens()) == null;
  }
}
