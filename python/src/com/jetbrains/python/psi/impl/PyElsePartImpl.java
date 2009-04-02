package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.PyElementTypes;
import com.intellij.lang.ASTNode;

/**
 * User: dcheryasov
 * Date: Mar 15, 2009 9:40:35 PM
 */
public class PyElsePartImpl extends PyElementImpl implements PyElsePart {
  
  public PyElsePartImpl(ASTNode astNode) {
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
