package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiListLikeElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyKeyValuePattern;
import com.jetbrains.python.psi.PyMappingPattern;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class PyMappingPatternImpl extends PyElementImpl implements PyMappingPattern, PsiListLikeElement {
  public PyMappingPatternImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyMappingPattern(this);
  }

  @Override
  public @NotNull List<? extends PsiElement> getComponents() {
    return Arrays.asList(findChildrenByClass(PyKeyValuePattern.class));
  }
}
