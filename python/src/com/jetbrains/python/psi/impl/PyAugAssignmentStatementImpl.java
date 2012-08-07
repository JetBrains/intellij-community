package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyAugAssignmentStatementImpl extends PyElementImpl implements PyAugAssignmentStatement {
  public PyAugAssignmentStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyAugAssignmentStatement(this);
  }

  @NotNull
  public PyExpression getTarget() {
    final PyExpression target = childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 0);
    if (target == null) {
      throw new RuntimeException("Target missing in augmented assignment statement");
    }
    return target;
  }

  @Nullable
  public PyExpression getValue() {
    return childToPsi(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens(), 1);
  }

  @Nullable
  public PsiElement getOperation() {
    return PyUtil.getChildByFilter(this, PyTokenTypes.AUG_ASSIGN_OPERATIONS, 0);
  }
}
