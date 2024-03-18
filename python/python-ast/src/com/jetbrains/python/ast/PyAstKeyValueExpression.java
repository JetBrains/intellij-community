// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstKeyValueExpression extends PyAstExpression {
  PyAstKeyValueExpression[] EMPTY_ARRAY = new PyAstKeyValueExpression[0];

  @NotNull
  default PyAstExpression getKey() {
    return (PyAstExpression)getNode().getFirstChildNode().getPsi();
  }

  @Nullable
  default PyAstExpression getValue() {
    return PsiTreeUtil.getNextSiblingOfType(getKey(), PyAstExpression.class);
  }
}
