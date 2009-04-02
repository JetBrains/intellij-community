package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyFinallyPart;

/**
 * User: dcheryasov
 * Date: Mar 16, 2009 6:58:03 AM
 */
public class PyFinallyPartImpl extends PyStatementPartImpl implements PyFinallyPart{
  public PyFinallyPartImpl(ASTNode astNode) {
    super(astNode);
  }
}
