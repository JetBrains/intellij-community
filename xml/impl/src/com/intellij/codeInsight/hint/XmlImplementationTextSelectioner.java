// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.hint;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class XmlImplementationTextSelectioner implements ImplementationTextSelectioner {
  private static final Logger LOG = Logger.getInstance(XmlImplementationTextSelectioner.class);

  @Override
  public int getTextStartOffset(final @NotNull PsiElement parent) {
    return parent.getTextRange().getStartOffset();
  }

  @Override
  public int getTextEndOffset(@NotNull PsiElement element) {
    if (element instanceof XmlAttributeValue) {
      final XmlTag xmlTag = PsiTreeUtil.getParentOfType(element, XmlTag.class);// for convenience
      if (xmlTag != null) return xmlTag.getTextRange().getEndOffset();
      LOG.assertTrue(false);
    }
    return element.getTextRange().getEndOffset();
  }
}