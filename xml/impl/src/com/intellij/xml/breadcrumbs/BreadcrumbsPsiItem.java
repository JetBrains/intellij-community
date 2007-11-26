package com.intellij.xml.breadcrumbs;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class BreadcrumbsPsiItem extends BreadcrumbsItem {
  private PsiElement myElement;
  private BreadcrumbsInfoProvider myProvider;

  public BreadcrumbsPsiItem(@NotNull final PsiElement element, @NotNull final BreadcrumbsInfoProvider provider) {
    myElement = element;
    myProvider = provider;
  }

  public String getDisplayText() {
    return myProvider.getElementInfo(myElement);
  }

  public String getTooltip() {
    final String s = myProvider.getElementTooltip(myElement);
    return s == null ? "" : s;
  }

  public boolean isValid() {
    return myElement != null && myElement.isValid();
  }

  public PsiElement getPsiElement() {
    return myElement;
  }
}
