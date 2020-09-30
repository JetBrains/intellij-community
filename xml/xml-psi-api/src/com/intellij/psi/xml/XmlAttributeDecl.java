// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.meta.PsiMetaOwner;

public interface XmlAttributeDecl extends XmlElement, PsiMetaOwner, PsiNamedElement {
  XmlElement getNameElement();
  XmlAttributeValue getDefaultValue();
  @NlsSafe String getDefaultValueText();

  boolean isAttributeRequired();
  boolean isAttributeFixed();
  boolean isAttributeImplied();

  boolean isEnumerated();
  XmlElement[] getEnumeratedValues();

  boolean isIdAttribute();
  boolean isIdRefAttribute();
}
