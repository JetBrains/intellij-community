package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.impl.references.PyKeywordPatternReference;

public class PyKeywordPatternImpl extends PyElementImpl implements PyKeywordPattern {
  public PyKeywordPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeywordPattern(this);
  }

  @Override
  public PsiReference getReference() {
    return new PyKeywordPatternReference(this);
  }
}
