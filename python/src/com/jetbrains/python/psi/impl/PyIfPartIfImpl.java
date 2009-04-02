package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyIfPart;

/**
 * PyIfPart that represents an 'if' part.
 * User: dcheryasov
 * Date: Mar 12, 2009 2:33:39 AM
 */
public class PyIfPartIfImpl extends PyConditionalStatementPartImpl implements PyIfPart {

  public PyIfPartIfImpl(ASTNode astNode) {
    super(astNode);
  }

  public boolean isElif() {
    return false;  
  }

}
