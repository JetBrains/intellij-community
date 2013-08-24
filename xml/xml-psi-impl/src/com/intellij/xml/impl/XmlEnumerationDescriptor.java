package com.intellij.xml.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlEnumeratedValueReference;

/**
 * @author Dmitry Avdeev
 *         Date: 22.08.13
 */
public abstract class XmlEnumerationDescriptor {

  public abstract boolean isFixed();

  public abstract String getDefaultValue();

  public abstract String[] getEnumeratedValues();

  public PsiElement getValueDeclaration(XmlElement attributeValue, String value) {
    String defaultValue = getDefaultValue();
    if (Comparing.equal(defaultValue, value)) {
      return getDefaultValueDeclaration();
    }
    return isFixed() ? null : getEnumeratedValueDeclaration(attributeValue, value);
  }

  protected abstract PsiElement getEnumeratedValueDeclaration(XmlElement value, String s);

  protected abstract PsiElement getDefaultValueDeclaration();

  public PsiReference[] getValueReferences(XmlAttributeValue value) {
    return new PsiReference[] { new XmlEnumeratedValueReference(value, this)};
  }

}
