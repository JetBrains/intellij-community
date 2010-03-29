package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyAssertStatement;

/**
 * @author yole
 */
public class PyAssertStatementImpl extends PyElementImpl implements PyAssertStatement {
  public PyAssertStatementImpl(ASTNode astNode) {
    super(astNode);
  }
}
