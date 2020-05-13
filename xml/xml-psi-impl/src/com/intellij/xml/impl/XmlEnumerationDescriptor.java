// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.xml.util.XmlEnumeratedReferenceSet;
import java.util.Objects;
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
    if (Objects.equals(defaultValue, value)) {
      return getDefaultValueDeclaration();
    }
    return isFixed() ? null : getEnumeratedValueDeclaration(attributeValue, value);
  }

  protected abstract PsiElement getEnumeratedValueDeclaration(XmlElement value, String s);

  protected abstract PsiElement getDefaultValueDeclaration();

  public boolean isList() { return false; }

  public PsiReference[] getValueReferences(T element, @NotNull String text) {
    return new XmlEnumeratedReferenceSet(element, this).getPsiReferences();
  }
}
