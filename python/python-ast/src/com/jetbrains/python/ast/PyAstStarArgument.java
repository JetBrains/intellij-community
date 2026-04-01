// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;


import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface PyAstStarArgument extends PyAstExpression {

  /**
   * Returns the expression being unpacked by this star argument.
   * <p>
   * For {@code *args}, returns the expression {@code args}.
   * For {@code **kwargs}, returns the expression {@code kwargs}.
   *
   * @return the inner expression, or {@code null} if the argument is incomplete
   */
  default @Nullable PyAstExpression getExpression() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  default boolean isKeyword() {
    return getNode().findChildByType(PyTokenTypes.EXP) != null;
  }
}
