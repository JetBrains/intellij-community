package com.intellij.refactoring.util;

import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class XmlNonCodeSearchElementDescriptionProvider implements ElementDescriptionProvider {
  public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
    if (!(location instanceof NonCodeSearchDescriptionLocation)) return null;
    final NonCodeSearchDescriptionLocation ncdLocation = (NonCodeSearchDescriptionLocation)location;
    if (ncdLocation.isNonJava()) return null;
    if (element instanceof XmlTag) {
      return ((XmlTag)element).getValue().getTrimmedText();
    }
    else if (element instanceof XmlAttribute) {
      return ((XmlAttribute)element).getValue();
    }
    else if (element instanceof XmlAttributeValue) {
      return ((XmlAttributeValue)element).getValue();
    }
    return null;
  }
}
