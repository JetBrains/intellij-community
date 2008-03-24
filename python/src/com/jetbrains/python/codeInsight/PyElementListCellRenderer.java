package com.jetbrains.python.codeInsight;

import com.intellij.ide.util.PsiElementListCellRenderer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

/**
 * @author yole
 */
public class PyElementListCellRenderer extends PsiElementListCellRenderer {
  public String getElementText(final PsiElement element) {
    final String name = ((PsiNamedElement)element).getName();
    return name == null ? "" : name;
  }

  protected String getContainerText(final PsiElement element, final String name) {
    if (element instanceof NavigationItem) {
      final ItemPresentation presentation = ((NavigationItem)element).getPresentation();
      if (presentation != null) {
        return presentation.getLocationString();
      }
    }
    return null;
  }

  protected int getIconFlags() {
    return 0;
  }
}
