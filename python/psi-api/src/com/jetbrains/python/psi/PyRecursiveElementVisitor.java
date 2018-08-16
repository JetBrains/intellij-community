// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveVisitor;

/**
 * @author yole
 */
public class PyRecursiveElementVisitor extends PyElementVisitor implements PsiRecursiveVisitor {
  @Override
  public void visitElement(final PsiElement element) {
    element.acceptChildren(this);
  }
}
