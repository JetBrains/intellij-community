package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyWhilePart;

/**
 * User: dcheryasov
 * Date: Mar 16, 2009 4:33:32 AM
 */
public class PyWhilePartImpl extends PyConditionalStatementPartImpl implements PyWhilePart {
  public PyWhilePartImpl(ASTNode astNode) {
    super(astNode);
  }
}
