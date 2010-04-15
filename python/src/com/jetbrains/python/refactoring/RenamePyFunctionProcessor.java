package com.jetbrains.python.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.jetbrains.python.psi.PyFunction;

/**
 * @author yole
 */
public class RenamePyFunctionProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(PsiElement element) {
    return element instanceof PyFunction;
  }

  @Override
  public boolean forcesShowPreview() {
    return true;
  }
}
