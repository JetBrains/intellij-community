// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;

public interface XmlDoctype extends XmlElement {
  XmlElement getNameElement();
  @NlsSafe String getDtdUri();
  PsiElement getDtdUrlElement();
  XmlMarkupDecl getMarkupDecl();
  @NlsSafe String getPublicId();
  @NlsSafe String getSystemId();
}
