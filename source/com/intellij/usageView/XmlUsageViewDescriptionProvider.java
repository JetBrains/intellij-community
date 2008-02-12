package com.intellij.usageView;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class XmlUsageViewDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (location instanceof UsageViewShortNameLocation) {
      if (element instanceof XmlAttributeValue) {
        return ((XmlAttributeValue)element).getValue();
      }
    }

    if (location instanceof UsageViewLongNameLocation) {
      if (element instanceof XmlTag) {
        return ((XmlTag)element).getName();
      }
      else if (element instanceof XmlAttributeValue) {
        return ((XmlAttributeValue)element).getValue();
      }
    }
    return null;
  }
}
