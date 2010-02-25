package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyNonlocalStatement;

/**
 * @author yole
 */
public class PyNonlocalStatementImpl extends PyElementImpl implements PyNonlocalStatement {
  public PyNonlocalStatementImpl(ASTNode astNode) {
    super(astNode);
  }

  public void accept(PyElementVisitor visitor) {
    visitor.visitPyNonlocalStatement(this);
  }
}
