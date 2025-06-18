// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression {

  public PyBinaryExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBinaryExpression(this);
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    PyExpression left = getLeftExpression();
    PyExpression right = getRightExpression();
    if (left == child.getPsi() && right != null) {
      replace(right);
    }
    else if (right == child.getPsi() && left != null) {
      replace(left);
    }
    else {
      throw new IncorrectOperationException("Element " + child.getPsi() + " is neither left expression or right expression");
    }
  }

  @Override
  public @NotNull PsiPolyVariantReference getReference() {
    return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
  }

  @Override
  public @NotNull PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  @Override
  public @Nullable PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    if (isOperator("and") || isOperator("or")) {
      final PyExpression left = getLeftExpression();
      final PyType leftType = left != null ? context.getType(left) : null;
      final PyExpression right = getRightExpression();
      final PyType rightType = right != null ? context.getType(right) : null;
      if (leftType == null && rightType == null) {
        return null;
      }
      return PyUnionType.union(leftType, rightType);
    }
    final String referencedName = getReferencedName();
    if (PyNames.CONTAINS.equals(referencedName)) {
      return PyBuiltinCache.getInstance(this).getBoolType();
    }
    PyType callResultType = PyCallExpressionHelper.getCallType(this, context, key);
    if (callResultType != null) {
      boolean bothOperandsAreKnown = operandIsKnown(getLeftExpression(), context) && operandIsKnown(getRightExpression(), context);
      // TODO requires weak union. See PyTypeCheckerInspectionTest#testBinaryExpressionWithUnknownOperand
      return bothOperandsAreKnown ? callResultType : PyUnionType.createWeakType(callResultType);
    }
    if (referencedName != null && PyNames.COMPARISON_OPERATORS.contains(referencedName)) {
      return PyBuiltinCache.getInstance(this).getBoolType();
    }
    return null;
  }

  private static boolean operandIsKnown(@Nullable PyExpression operand, @NotNull TypeEvalContext context) {
    if (operand == null) return false;

    final PyType operandType = context.getType(operand);
    if (operandType instanceof PyStructuralType || PyTypeChecker.isUnknown(operandType, context)) return false;

    return true;
  }
}
