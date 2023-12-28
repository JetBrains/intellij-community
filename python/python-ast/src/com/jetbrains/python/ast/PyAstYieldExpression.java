// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstYieldExpression extends PyAstExpression {
  @Nullable
  default PyAstExpression getExpression() {
    final PyAstExpression[] expressions = PsiTreeUtil.getChildrenOfType(this, PyAstExpression.class);
    return (expressions != null && expressions.length > 0) ? expressions[0] : null;
  }

  default boolean isDelegating() {
    return getNode().findChildByType(PyTokenTypes.FROM_KEYWORD) != null;
  }
}
