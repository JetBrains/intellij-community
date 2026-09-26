// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.editor.selectWord;

import com.intellij.codeInsight.editorActions.wordSelection.AbstractWordSelectioner;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;


public final class PyWordSelectionHandler extends AbstractWordSelectioner {
  @Override
  public boolean canSelect(final @NotNull PsiElement e) {
    final ASTNode astNode = e.getNode();
    return astNode != null && astNode.getElementType() == PyTokenTypes.IDENTIFIER;
  }
}
