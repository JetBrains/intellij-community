// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;

public interface XmlElement extends PsiElement {
  Key<XmlElement> INCLUDING_ELEMENT = Key.create("INCLUDING_ELEMENT");
  Key<PsiElement> DEPENDING_ELEMENT = Key.create("DEPENDING_ELEMENT");
  Key<Boolean> DO_NOT_VALIDATE = Key.create("do not validate");

  XmlElement[] EMPTY_ARRAY = new XmlElement[0];

  boolean processElements(PsiElementProcessor processor, PsiElement place);
  default boolean skipValidation() {
    return false;
  }
}
