package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeywordPattern;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.impl.references.PyKeywordPatternReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyKeywordPatternImpl extends PyElementImpl implements PyKeywordPattern {
  public PyKeywordPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyKeywordPattern(this);
  }

  @Override
  public @NotNull String getKeyword() {
    return getKeywordElement().getText();
  }

  @Override
  public @NotNull PsiElement getKeywordElement() {
    return getFirstChild();
  }

  @Override
  public @Nullable PyPattern getValuePattern() {
    return findChildByClass(PyPattern.class);
  }

  @Override
  public PsiReference getReference() {
    return new PyKeywordPatternReference(this);
  }

  @Override
  public boolean isIrrefutable() {
    return false;
  }
}
