// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@SuppressWarnings("MissingDeprecatedAnnotation")
@Deprecated(forRemoval = true)
@ApiStatus.Experimental
public interface PyAstSliceExpression extends PyAstExpression {
  default @NotNull PyAstExpression getOperand() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
  }

  default @Nullable PyAstSliceItem getSliceItem() {
    return PsiTreeUtil.getChildOfType(this, PyAstSliceItem.class);
  }
}
