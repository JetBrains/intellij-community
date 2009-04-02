package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyIfPart;

/**
 * PyIfPart that represents an 'elif' part.
 * User: dcheryasov
 * Date: Mar 12, 2009 5:21:11 PM
 */
public class PyIfPartElifImpl extends PyConditionalStatementPartImpl implements PyIfPart {
  public PyIfPartElifImpl(ASTNode astNode) {
    super(astNode);
  }

  public boolean isElif() {
    return true;
  }
}
