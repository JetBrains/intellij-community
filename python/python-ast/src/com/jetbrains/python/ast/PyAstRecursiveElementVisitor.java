package com.jetbrains.python.ast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveVisitor;
import org.jetbrains.annotations.NotNull;

public class PyAstRecursiveElementVisitor extends PyAstElementVisitor implements PsiRecursiveVisitor {
  @Override
  public void visitElement(final @NotNull PsiElement element) {
    element.acceptChildren(this);
  }
}
