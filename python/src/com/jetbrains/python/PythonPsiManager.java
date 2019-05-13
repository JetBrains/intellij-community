// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.PsiTreeChangePreprocessorBase;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import org.jetbrains.annotations.NotNull;

public class PythonPsiManager extends PsiTreeChangePreprocessorBase {
  public PythonPsiManager(@NotNull PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean acceptsEvent(@NotNull PsiTreeChangeEventImpl event) {
    return event.getFile() instanceof PyFile;
  }

  @Override
  protected boolean isOutOfCodeBlock(@NotNull PsiElement element) {
    while (true) {
      if (element instanceof PyFile) {
        return true;
      }
      if (element instanceof PsiFile || element instanceof PsiDirectory || element == null) {
        return false;
      }
      PsiElement pparent = element.getParent();
      if (pparent instanceof PyFunction) {
        PyFunction pyFunction = (PyFunction)pparent;
        return element == pyFunction.getParameterList() || element == pyFunction.getNameIdentifier();
      }
      element = pparent;
    }
  }
}
