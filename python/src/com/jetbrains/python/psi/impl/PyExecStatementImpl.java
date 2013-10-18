package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyExecStatement;

/**
 * @author yole
 */
public class PyExecStatementImpl extends PyElementImpl implements PyExecStatement {
  public PyExecStatementImpl(ASTNode astNode) {
    super(astNode);
  }
}
