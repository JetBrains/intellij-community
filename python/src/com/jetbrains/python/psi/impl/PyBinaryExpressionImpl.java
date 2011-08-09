package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyBinaryExpressionImpl extends PyElementImpl implements PyBinaryExpression, PyQualifiedExpression {

  public PyBinaryExpressionImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyBinaryExpression(this);
  }

  public PyExpression getLeftExpression() {
    return PsiTreeUtil.getChildOfType(this, PyExpression.class);
  }

  public PyExpression getRightExpression() {
    return PsiTreeUtil.getNextSiblingOfType(getLeftExpression(), PyExpression.class);
  }

  @Nullable
  public PyElementType getOperator() {
    final PsiElement psiOperator = getPsiOperator();
    return psiOperator != null ? (PyElementType)psiOperator.getNode().getElementType() : null;
  }

  @Nullable
  public PsiElement getPsiOperator() {
    ASTNode node = getNode();
    final ASTNode child = node.findChildByType(PyElementTypes.BINARY_OPS);
    if (child != null) return child.getPsi();
    return null;
  }

  public boolean isOperator(String chars) {
    ASTNode child = getNode().getFirstChildNode();
    StringBuffer buf = new StringBuffer();
    while (child != null) {
      IElementType elType = child.getElementType();
      if (elType instanceof PyElementType && PyElementTypes.BINARY_OPS.contains(elType)) {
        buf.append(child.getText());
      }
      child = child.getTreeNext();
    }
    return buf.toString().equals(chars);
  }

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
    if (left == child.getPsi()) {
      replace(right);
    }
    else if (right == child.getPsi()) {
      replace(left);
    }
    else {
      throw new IncorrectOperationException("Element " + child.getPsi() + " is neither left expression or right expression");
    }
  }

  @Override
  public PsiReference getReference() {
    return getReference(PyResolveContext.noImplicits().withTypeEvalContext(TypeEvalContext.slow()));
  }

  public PsiReference getReference(PyResolveContext resolveContext) {
    final PyElementType t = getOperator();
    if (t != null && t.getSpecialMethodName() != null) {
      return new PyQualifiedReferenceImpl(this, resolveContext);
    }
    return null;
  }

  public PyType getType(@NotNull TypeEvalContext context) {
    final PyExpression lhs = getLeftExpression();
    final PyExpression rhs = getRightExpression();
    final PsiElement operator = getPsiOperator();
    if (lhs != null && rhs != null && operator != null) {
      final PsiReference ref = getReference(PyResolveContext.noImplicits().withTypeEvalContext(context));
      if (ref != null) {
        final PsiElement resolved = ref.resolve();
        if (resolved instanceof Callable) {
          final TypeEvalContext returnTypeContext = resolved.getContainingFile() == getContainingFile() ?
                                                    context : TypeEvalContext.fast();
          return ((Callable)resolved).getReturnType(returnTypeContext, null);
        }
      }
    }
    return null;
  }

  @Override
  public PyExpression getQualifier() {
    return getLeftExpression();
  }

  @Override
  public String getReferencedName() {
    final PyElementType t = getOperator();
    return t != null ? t.getSpecialMethodName() : null;
  }

  @Override
  public ASTNode getNameElement() {
    final PsiElement op = getPsiOperator();
    return op != null ? op.getNode() : null;
  }
}
