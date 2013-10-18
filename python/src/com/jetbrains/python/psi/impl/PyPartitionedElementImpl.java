package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyStatementPart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common parts functionality.
 * User: dcheryasov
 * Date: Mar 19, 2009 2:51:15 AM
 */
public class PyPartitionedElementImpl extends PyElementImpl {
  public PyPartitionedElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @NotNull
  PyStatementPart[] getParts() {
    return childrenToPsi(PyElementTypes.PARTS, PyStatementPart.EMPTY_ARRAY);
  }

  @Nullable
  protected PyStatementPart getPart(PyElementType which) {
    ASTNode n = getNode().findChildByType(which);
    if (n == null) return null;
    return (PyStatementPart)n.getPsi();
  }

  @NotNull
  protected PyStatementPart getPartNotNull(PyElementType which) {
    ASTNode n = getNode().findChildByType(which);
    assert n != null;
    return (PyStatementPart)n.getPsi();
  }

}
