package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.psi.*;

class GotoFileCellRenderer extends PsiElementListCellRenderer{
  public String getElementText(PsiElement element) {
    final PsiFile file = (PsiFile)element;
    return file.getName();
  }

  protected String getContainerText(PsiElement element) {
    PsiFile file = (PsiFile)element;
    String path = "(" + file.getContainingDirectory().getVirtualFile().getPresentableUrl() + ")";
    return path;
  }

  protected int getIconFlags() {
    return 0;
  }
}