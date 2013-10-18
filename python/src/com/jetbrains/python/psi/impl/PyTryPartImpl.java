package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyTryPart;

/**
 * User: dcheryasov
 * Date: Mar 16, 2009 6:56:55 AM
 */
public class PyTryPartImpl extends PyStatementPartImpl implements PyTryPart {
  public PyTryPartImpl(ASTNode astNode) {
    super(astNode);
  }
}
