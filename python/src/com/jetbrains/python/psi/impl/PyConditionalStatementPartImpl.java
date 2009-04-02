package com.jetbrains.python.psi.impl;

import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.PyElementTypes;
import com.intellij.lang.ASTNode;

/**
 * User: dcheryasov
 * Date: Mar 16, 2009 4:46:26 AM
 */
public abstract class PyConditionalStatementPartImpl extends PyStatementPartImpl implements PyConditionalStatementPart {
  public PyConditionalStatementPartImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyExpression getCondition() {
    ASTNode n = getNode().findChildByType(PyElementTypes.EXPRESSIONS);
    if (n != null) {
      return (PyExpression)n.getPsi();
    }
    return null;
  }
}
