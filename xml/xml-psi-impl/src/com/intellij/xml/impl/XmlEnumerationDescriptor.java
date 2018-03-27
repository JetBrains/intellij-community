package com.intellij.xml.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlEnumeratedValueReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class XmlEnumerationDescriptor<T extends XmlElement> {

  public abstract boolean isFixed();

  public abstract String getDefaultValue();

  public abstract boolean isEnumerated(@Nullable XmlElement context);

  public abstract String[] getEnumeratedValues();

  public String[] getValuesForCompletion() {
    return StringUtil.filterEmptyStrings(getEnumeratedValues());
  }

  public PsiElement getValueDeclaration(XmlElement attributeValue, String value) {
    String defaultValue = getDefaultValue();
    if (Comparing.equal(defaultValue, value)) {
      return getDefaultValueDeclaration();
    }
    return isFixed() ? null : getEnumeratedValueDeclaration(attributeValue, value);
  }

  protected abstract PsiElement getEnumeratedValueDeclaration(XmlElement value, String s);

  protected abstract PsiElement getDefaultValueDeclaration();

  public PsiReference[] getValueReferences(T element, @NotNull String text) {
    return new PsiReference[] { new XmlEnumeratedValueReference(element, this)};
  }
}
