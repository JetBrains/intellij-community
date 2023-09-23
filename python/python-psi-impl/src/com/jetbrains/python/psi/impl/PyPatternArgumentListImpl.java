package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyPatternArgumentList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class PyPatternArgumentListImpl extends PyElementImpl implements PyPatternArgumentList, PsiListLikeElement {
  public PyPatternArgumentListImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyPatternArgumentList(this);
  }

  @Override
  public @NotNull List<PyPattern> getPatterns() {
    return Arrays.asList(findChildrenByClass(PyPattern.class));
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return getPatterns();
  }
}
