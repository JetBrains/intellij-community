// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public abstract class BasicXmlAttributeDescriptor extends XmlEnumerationDescriptor implements XmlAttributeDescriptor {
  @Override
  public @Nls String validateValue(XmlElement context, String value) {
    return null;
  }

  @Override
  public String getName(PsiElement context){
    return getName();
  }

  public String @Nullable [] getEnumeratedValues(@Nullable XmlElement context) {
    return getEnumeratedValues();
  }

  @Override
  public boolean isEnumerated(XmlElement context) {
    return isEnumerated();
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  protected PsiElement getEnumeratedValueDeclaration(XmlElement xmlElement, String value) {
    String[] values = getEnumeratedValues();
    if (values == null || values.length == 0) return getFirstItem(getDeclarations());
    return ArrayUtilRt.find(values, value) != -1 ? getFirstItem(getDeclarations()) : null;
  }

  @Override
  protected PsiElement getDefaultValueDeclaration() {
    return getFirstItem(getDeclarations());
  }
}
