// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.moveLeftRight;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

public class XmlMoveLeftRightHandler extends MoveElementLeftRightHandler {
  @Override
  public PsiElement @NotNull [] getMovableSubElements(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      return ((XmlTag)element).getAttributes().clone();
    }
    return PsiElement.EMPTY_ARRAY;
  }
}
