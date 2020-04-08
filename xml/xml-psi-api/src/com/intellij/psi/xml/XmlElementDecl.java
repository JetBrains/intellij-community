// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.meta.PsiMetaOwner;
import org.jetbrains.annotations.Nullable;


public interface XmlElementDecl extends XmlElement, PsiMetaOwner, PsiNameIdentifierOwner {
  XmlElement getNameElement();
  @Override
  @Nullable String getName();
  XmlElementContentSpec getContentSpecElement();
}
