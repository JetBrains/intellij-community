// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class XmlAnchorProvider extends SmartPointerAnchorProvider {
  @Override
  public @Nullable PsiElement getAnchor(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      return XmlTagUtil.getStartTagNameElement((XmlTag)element);
    }
    return null;
  }

  @Override
  public @Nullable PsiElement restoreElement(@NotNull PsiElement anchor) {
    if (anchor instanceof XmlToken token) {
      return token.getTokenType() == XmlTokenType.XML_NAME ? token.getParent() : null;
    }
    return null;
  }
}
