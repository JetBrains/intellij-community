package com.intellij.ide.util.gotoByName;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;

public class GotoSymbolCellRenderer extends PsiElementListCellRenderer {
  protected int getIconFlags() {
    return Iconable.ICON_FLAG_VISIBILITY;
  }

  public String getElementText(PsiElement element){
    return SymbolPresentationUtil.getSymbolPresentableText(element);
  }

  public String getContainerText(PsiElement element){
    return SymbolPresentationUtil.getSymbolContainerText(element);
  }

}