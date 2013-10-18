package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.stubs.StubElement;

/**
 * @author yole
 */
@SuppressWarnings("deprecation")
public class PyElementImpl extends PyBaseElementImpl<StubElement> {
  public PyElementImpl(ASTNode astNode) {
    super(astNode);
  }
}
