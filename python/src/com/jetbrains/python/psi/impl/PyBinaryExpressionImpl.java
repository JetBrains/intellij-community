/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression {

  public PyBinaryExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBinaryExpression(this);
  }

  @Override
  @Nullable
  public PyExpression getLeftExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  @Override
  @Nullable
  public PyExpression getRightExpression() {
    return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyExpression.class);
  }

  @Override
  @Nullable
  public PyElementType getOperator() {
    final PsiElement psiOperator = getPsiOperator();
    return psiOperator != null ? (PyElementType)psiOperator.getNode().getElementType() : null;
  }

  @Override
  @Nullable
  public PsiElement getPsiOperator() {
    ASTNode node = getNode();
    final ASTNode child = node.findChildByType(PyElementTypes.BINARY_OPS);
    if (child != null) return child.getPsi();
    return null;
  }

  @Override
  public boolean isOperator(String chars) {
    ASTNode child = getNode().getFirstChildNode();
    StringBuilder buf = new StringBuilder();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (elType instanceof PyElementType && PyElementTypes.BINARY_OPS.contains(elType)) {
        buf.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return buf.toString().equals(chars);
  }

  @Override
  @Nullable
  public PyExpression getOppositeExpression(PyExpression expression) throws IllegalArgumentException {
    PyExpression right = getRightExpression();
    PyExpression left = getLeftExpression();
    if (expression.equals(left)) {
      return right;
    }
    if (expression.equals(right)) {
      return left;
    }
    throw new IllegalArgumentException("expression " + expression + " is neither left exp or right exp");
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

  @NotNull
  @Override
  public PsiPolyVariantReference getReference() {
    return getReference(PyResolveContext.noImplicits());
  }

  @NotNull
  @Override
  public PsiPolyVariantReference getReference(@NotNull PyResolveContext context) {
    return new PyOperatorReference(this, context);
  }

  @Override
  @Nullable
  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
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
    final List<PyCallExpression.PyArgumentsMapping> results =
      PyCallExpressionHelper.mapArguments(this, PyResolveContext.noImplicits().withTypeEvalContext(context));
    if (!results.isEmpty()) {
      final List<PyType> types = new ArrayList<>();
      final List<PyType> matchedTypes = new ArrayList<>();
      for (PyCallExpression.PyArgumentsMapping result : results) {
        final PyCallExpression.PyMarkedCallee markedCallee = result.getMarkedCallee();
        if (markedCallee == null) continue;

        boolean matched = true;
        for (Map.Entry<PyExpression, PyCallableParameter> entry : result.getMappedParameters().entrySet()) {
          final PyType parameterType = entry.getValue().getArgumentType(context);
          final PyType argumentType = context.getType(entry.getKey());
          if (!PyTypeChecker.match(parameterType, argumentType, context, new HashMap<>())) {
            matched = false;
          }
        }
        final PyType type = markedCallee.getCallableType().getCallType(context, this);
        types.add(type);
        if (matched) {
          matchedTypes.add(type);
        }
      }
      final boolean bothOperandsAreKnown = operandIsKnown(getLeftExpression(), context) && operandIsKnown(getRightExpression(), context);
      final List<PyType> resultTypes = !matchedTypes.isEmpty() ? matchedTypes : types;
      if (!resultTypes.isEmpty()) {
        final PyType result = PyUnionType.union(resultTypes);
        return bothOperandsAreKnown ? result : PyUnionType.createWeakType(result);
      }
    }
    if (PyNames.COMPARISON_OPERATORS.contains(getReferencedName())) {
      return PyBuiltinCache.getInstance(this).getBoolType();
    }
    return null;
  }

  @Override
  public PyExpression getQualifier() {
    return getLeftExpression();
  }

  @Nullable
  @Override
  public QualifiedName asQualifiedName() {
    return PyPsiUtils.asQualifiedName(this);
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  public String getReferencedName() {
    final PyElementType t = getOperator();
    if (t == PyTokenTypes.DIV && isTrueDivEnabled(this)) {
      return PyNames.TRUEDIV;
    }
    return t != null ? t.getSpecialMethodName() : null;
  }

  @Override
  public ASTNode getNameElement() {
    final PsiElement op = getPsiOperator();
    return op != null ? op.getNode() : null;
  }

  @Nullable
  @Override
  public PyExpression getReceiver(@Nullable PyCallable resolvedCallee) {
    return isRightOperator(resolvedCallee) ? getRightExpression() : getChainedComparisonAwareLeftExpression();
  }

  @NotNull
  @Override
  public List<PyExpression> getArguments(@Nullable PyCallable resolvedCallee) {
    return Collections.singletonList(isRightOperator(resolvedCallee) ? getChainedComparisonAwareLeftExpression() : getRightExpression());
  }

  public boolean isRightOperator(@Nullable PyCallable resolvedCallee) {
    return resolvedCallee != null && PyNames.isRightOperatorName(getReferencedName(), resolvedCallee.getName());
  }

  @Nullable
  private PyExpression getChainedComparisonAwareLeftExpression() {
    final PyExpression leftOperand = getLeftExpression();
    if (PyTokenTypes.COMPARISON_OPERATIONS.contains(getOperator())) {
      final PyBinaryExpression leftBinaryExpr = as(leftOperand, PyBinaryExpression.class);
      if (leftBinaryExpr != null && PyTokenTypes.COMPARISON_OPERATIONS.contains(leftBinaryExpr.getOperator())) {
        return leftBinaryExpr.getRightExpression();
      }
    }
    return leftOperand;
  }

  private static boolean operandIsKnown(@Nullable PyExpression operand, @NotNull TypeEvalContext context) {
    if (operand == null) return false;

    final PyType operandType = context.getType(operand);
    if (operandType instanceof PyStructuralType || PyTypeChecker.isUnknown(operandType, context)) return false;

    return true;
  }

  private static boolean isTrueDivEnabled(@NotNull PyElement anchor) {
    final PsiFile file = anchor.getContainingFile();
    if (file instanceof PyFile) {
      final PyFile pyFile = (PyFile)file;
      return FutureFeature.DIVISION.requiredAt(pyFile.getLanguageLevel()) || pyFile.hasImportFromFuture(FutureFeature.DIVISION);
    }
    return false;
  }
}
