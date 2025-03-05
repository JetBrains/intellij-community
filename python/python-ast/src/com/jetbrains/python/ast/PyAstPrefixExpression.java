// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
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
  @Override
  default @Nullable PyAstExpression getReceiver(@Nullable PyAstCallable resolvedCallee) {
    return getOperand();
  }

  @Override
  default @NotNull List<PyAstExpression> getArguments(@Nullable PyAstCallable resolvedCallee) {
    return Collections.emptyList();
  }

  default @Nullable PyAstExpression getOperand() {
    return childToPsi(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens(), 0);
  }

  default @Nullable PsiElement getPsiOperator() {
    final ASTNode node = getNode();
    final ASTNode child = node.findChildByType(PyTokenTypes.UNARY_OPS);
    return child != null ? child.getPsi() : null;
  }

  default @NotNull PyElementType getOperator() {
    final PsiElement op = getPsiOperator();
    assert op != null;
    return (PyElementType)op.getNode().getElementType();
  }

  @Override
  default PyAstExpression getQualifier() {
    return getOperand();
  }

  @Override
  default @Nullable QualifiedName asQualifiedName() {
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

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyPrefixExpression(this);
  }
}
