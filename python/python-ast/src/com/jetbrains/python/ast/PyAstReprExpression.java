// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstReprExpression extends PyAstExpression {
  default @Nullable PyAstExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyReprExpression(this);
  }
}
