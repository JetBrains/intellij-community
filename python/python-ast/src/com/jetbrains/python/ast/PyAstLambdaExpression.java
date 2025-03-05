// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


@ApiStatus.Experimental
public interface PyAstLambdaExpression extends PyAstExpression, PyAstCallable, AstScopeOwner {
  @Override
  default @NotNull PyAstParameterList getParameterList() {
    final PyAstElement child = childToPsi(PyElementTypes.PARAMETER_LIST_SET, 0);
    if (child == null) {
      throw new RuntimeException("parameter list must not be null; text=" + getText());
    }
    return (PyAstParameterList)child;
  }

  default @Nullable PyAstExpression getBody() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  @Override
  default @Nullable PyAstFunction asMethod() {
    return null; // we're never a method
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyLambdaExpression(this);
  }
}
