package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyStatementPart;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.PyElementTypes;
import com.intellij.lang.ASTNode;

/**
 * Abstract statement part implementation; extracts the statements list.
 * User: dcheryasov
 * Date: Mar 16, 2009 4:36:50 AM
 */
public abstract class PyStatementPartImpl extends PyElementImpl implements PyStatementPart {
  protected PyStatementPartImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyStatementList getStatementList() {
    ASTNode n = getNode().findChildByType(PyElementTypes.STATEMENT_LISTS);
    if (n != null) {
      return (PyStatementList)n.getPsi();
    }
    return null;
  }
}
