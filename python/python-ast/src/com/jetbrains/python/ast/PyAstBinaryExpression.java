// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;


@ApiStatus.Experimental
public interface PyAstBinaryExpression extends PyAstQualifiedExpression, PyAstCallSiteExpression, PyAstReferenceOwner {

  default PyAstExpression getLeftExpression() {
    return PsiTreeUtil.getChildOfType(this, PyAstExpression.class);
  }

  default @Nullable PyAstExpression getRightExpression() {
    return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyAstExpression.class);
  }

  default @Nullable PyElementType getOperator() {
    final PsiElement psiOperator = getPsiOperator();
    return psiOperator != null ? (PyElementType)psiOperator.getNode().getElementType() : null;
  }

  default @Nullable PsiElement getPsiOperator() {
    ASTNode node = getNode();
    final ASTNode child = node.findChildByType(PyTokenTypes.BINARY_OPS);
    if (child != null) return child.getPsi();
    return null;
  }

  default boolean isOperator(String chars) {
    ASTNode child = getNode().getFirstChildNode();
    StringBuilder buf = new StringBuilder();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (elType instanceof PyElementType && PyTokenTypes.BINARY_OPS.contains(elType)) {
        buf.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return buf.toString().equals(chars);
  }

  default @Nullable PyAstExpression getOppositeExpression(PyAstExpression expression) throws IllegalArgumentException {
    PyAstExpression right = getRightExpression();
    PyAstExpression left = getLeftExpression();
    if (expression.equals(left)) {
      return right;
    }
    if (expression.equals(right)) {
      return left;
    }
    throw new IllegalArgumentException("expression " + expression + " is neither left exp or right exp");
  }

  default boolean isRightOperator(@Nullable PyAstCallable resolvedCallee) {
    return resolvedCallee != null && PyNames.isRightOperatorName(getReferencedName(), resolvedCallee.getName());
  }

  @Override
  default PyAstExpression getQualifier() {
    return getLeftExpression();
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
    if (t == PyTokenTypes.DIV && isTrueDivEnabled(this)) {
      return PyNames.TRUEDIV;
    }
    return t != null ? t.getSpecialMethodName() : null;
  }

  @Override
  default ASTNode getNameElement() {
    final PsiElement op = getPsiOperator();
    return op != null ? op.getNode() : null;
  }

  @Override
  default @Nullable PyAstExpression getReceiver(@Nullable PyAstCallable resolvedCallee) {
    return isRightOperator(resolvedCallee) ? getRightExpression() : getChainedComparisonAwareLeftExpression();
  }

  @Override
  default @NotNull List<? extends PyAstExpression> getArguments(@Nullable PyAstCallable resolvedCallee) {
    return Collections.singletonList(isRightOperator(resolvedCallee) ? getChainedComparisonAwareLeftExpression() : getRightExpression());
  }

  private @Nullable PyAstExpression getChainedComparisonAwareLeftExpression() {
    final PyAstExpression leftOperand = getLeftExpression();
    if (PyTokenTypes.COMPARISON_OPERATIONS.contains(getOperator())) {
      final PyAstBinaryExpression leftBinaryExpr = ObjectUtils.tryCast(leftOperand, PyAstBinaryExpression.class);
      if (leftBinaryExpr != null && PyTokenTypes.COMPARISON_OPERATIONS.contains(leftBinaryExpr.getOperator())) {
        return leftBinaryExpr.getRightExpression();
      }
    }
    return leftOperand;
  }

  private static boolean isTrueDivEnabled(@NotNull PyAstElement anchor) {
    final PsiFile file = anchor.getContainingFile();
    if (file instanceof PyAstFile pyFile) {
      return FutureFeature.DIVISION.requiredAt(pyFile.getLanguageLevel()) || pyFile.hasImportFromFuture(FutureFeature.DIVISION);
    }
    return false;
  }

  @Override
  default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
    pyVisitor.visitPyBinaryExpression(this);
  }
}
