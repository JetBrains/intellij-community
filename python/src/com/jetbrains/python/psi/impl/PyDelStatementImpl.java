package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyDelStatement;
import org.jetbrains.annotations.Nullable;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;

/**
 * @author yole
 */
public class PyDelStatementImpl extends PyElementImpl implements PyDelStatement {
  public PyDelStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyDelStatement(this);
  }

  @Nullable
  public PyExpression[] getTargets() {
    return childrenToPsi(PyElementTypes.EXPRESSIONS, PyExpression.EMPTY_ARRAY);
  }
}
