package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyWildcardPattern;

public class PyWildcardPatternImpl extends PyElementImpl implements PyWildcardPattern {
  public PyWildcardPatternImpl(ASTNode astNode) {
    super(astNode);
  }
}
