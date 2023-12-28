// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.AccessDirection;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


@ApiStatus.Experimental
public interface PyAstSubscriptionExpression extends PyAstQualifiedExpression, PyAstCallSiteExpression, PyAstReferenceOwner {

  @Nullable
  @Override
  default PyAstExpression getReceiver(@Nullable PyAstCallable resolvedCallee) {
    return getOperand();
  }

  @NotNull
  @Override
  default List<PyAstExpression> getArguments(@Nullable PyAstCallable resolvedCallee) {
    if (AccessDirection.of(this) == AccessDirection.WRITE) {
      final PsiElement parent = getParent();
      if (parent instanceof PyAstAssignmentStatement) {
        return Arrays.asList(getIndexExpression(), ((PyAstAssignmentStatement)parent).getAssignedValue());
      }
    }
    return Collections.singletonList(getIndexExpression());
  }

  /**
   * @return For {@code spam[x][y][n]} will return {@code spam} regardless number of its dimensions
   */
  @NotNull
  default PyAstExpression getRootOperand() {
    PyAstExpression operand = getOperand();
    while (operand instanceof PyAstSubscriptionExpression) {
      operand = ((PyAstSubscriptionExpression)operand).getOperand();
    }
    return operand;
  }

  @NotNull
  default PyAstExpression getOperand() {
    return childToPsiNotNull(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
  }

  @Nullable
  default PyAstExpression getIndexExpression() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 1);
  }

  @Override
  default PyAstExpression getQualifier() {
    return getOperand();
  }

  @Nullable
  @Override
  default QualifiedName asQualifiedName() {
    return PyPsiUtilsCore.asQualifiedName(this);
  }

  @Override
  default boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  default String getReferencedName() {
    return switch (AccessDirection.of(this)) {
      case READ -> PyNames.GETITEM;
      case WRITE -> PyNames.SETITEM;
      case DELETE -> PyNames.DELITEM;
    };
  }

  @Override
  default ASTNode getNameElement() {
    return getNode().findChildByType(PyTokenTypes.LBRACKET);
  }
}
