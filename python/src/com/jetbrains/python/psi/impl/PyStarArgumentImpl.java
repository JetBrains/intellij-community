package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.types.PyType;

/**
 * @author yole
 */
public class PyStarArgumentImpl extends PyElementImpl implements PyStarArgument {
  public PyStarArgumentImpl(ASTNode astNode) {
    super(astNode);
  }

  public PyType getType() {
    return null;
  }

  public boolean isKeyword() {
    return getNode().findChildByType(PyTokenTypes.EXP) != null;
  }
}
