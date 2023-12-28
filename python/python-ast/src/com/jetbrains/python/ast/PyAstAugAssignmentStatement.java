// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstAugAssignmentStatement extends PyAstStatement {
  @NotNull
  default PyAstExpression getTarget() {
    final PyAstExpression target = childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
    if (target == null) {
      throw new RuntimeException("Target missing in augmented assignment statement");
    }
    return target;
  }

  @Nullable
  default PyAstExpression getValue() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
  }

  @Nullable
  default PsiElement getOperation() {
    return PyPsiUtilsCore.getChildByFilter(this, PyTokenTypes.AUG_ASSIGN_OPERATIONS, 0);
  }
}
