package com.intellij.ide;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;

public interface IdeView {
  void selectElement(PsiElement element);
  PsiDirectory[] getDirectories();
}
