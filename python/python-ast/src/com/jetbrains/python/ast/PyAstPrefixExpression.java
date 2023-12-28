// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


@ApiStatus.Experimental
public interface PyAstPrefixExpression extends PyAstQualifiedExpression, PyAstReferenceOwner, PyAstCallSiteExpression {
  @Nullable
  @Override
  default PyAstExpression getReceiver(@Nullable PyAstCallable resolvedCallee) {
    return getOperand();
  }

  @NotNull
  @Override
  default List<PyAstExpression> getArguments(@Nullable PyAstCallable resolvedCallee) {
    return Collections.emptyList();
  }

  @Nullable
  default PyAstExpression getOperand() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
  }

  @Nullable
  default PsiElement getPsiOperator() {
    final ASTNode node = getNode();
    final ASTNode child = node.findChildByType(PyElementTypes.UNARY_OPS);
    return child != null ? child.getPsi() : null;
  }

  @NotNull
  default PyElementType getOperator() {
    final PsiElement op = getPsiOperator();
    assert op != null;
    return (PyElementType)op.getNode().getElementType();
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
    final PyElementType t = getOperator();
    if (t == PyTokenTypes.PLUS) {
      return PyNames.POS;
    }
    else if (t == PyTokenTypes.MINUS) {
      return PyNames.NEG;
    }
    return getOperator().getSpecialMethodName();
  }

  @Override
  default ASTNode getNameElement() {
    final PsiElement op = getPsiOperator();
    return op != null ? op.getNode() : null;
  }
}
