package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public class PyRecursiveElementVisitor extends PyElementVisitor {
  public void visitElement(final PsiElement element) {
    element.acceptChildren(this);
  }
}
