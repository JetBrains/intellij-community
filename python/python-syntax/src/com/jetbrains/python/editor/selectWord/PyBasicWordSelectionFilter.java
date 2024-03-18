// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor.selectWord;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;


public final class PyBasicWordSelectionFilter implements Condition<PsiElement> {
  @Override
  public boolean value(PsiElement element) {
    IElementType elementType = element.getNode().getElementType();
    if (PyTokenTypes.STRING_NODES.contains(elementType)) {
      return false;
    }
    return true;
  }
}
