package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyPassStatement;

/**
 * @author yole
 */
public class PyPassStatementImpl extends PyElementImpl implements PyPassStatement {
  public PyPassStatementImpl(ASTNode astNode) {
    super(astNode);
  }
}
