// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XmlTargetElementEvaluator extends TargetElementEvaluatorEx2 {
  @Override
  public boolean isAcceptableNamedParent(@NotNull PsiElement parent) {
    return !(parent instanceof XmlAttribute);
  }

  @Nullable
  @Override
  public PsiElement adjustElement(Editor editor, int flags, @Nullable PsiElement element, @Nullable PsiElement contextElement) {
    if (element != null) return element;
    if (contextElement == null) return null;

    final PsiElement parent = contextElement.getParent();
    if (parent instanceof XmlText || parent instanceof XmlAttributeValue) {
      final PsiElement gParent = parent.getParent();
      if (gParent == null) return null;
      return TargetElementUtil.getInstance().findTargetElement(editor, flags, gParent.getTextRange().getStartOffset() + 1);
    }
    else if (parent instanceof XmlTag || parent instanceof XmlAttribute) {
      return TargetElementUtil.getInstance().findTargetElement(editor, flags, parent.getTextRange().getStartOffset() + 1);
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement getElementByReference(@NotNull PsiReference ref, int flags) {
    return null;
  }
}
