package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.jetbrains.python.psi.PyAnnotation;

/**
 * @author yole
 */
public class PyAnnotationImpl extends PyElementImpl implements PyAnnotation {
  public PyAnnotationImpl(ASTNode astNode) {
    super(astNode);
  }
}
