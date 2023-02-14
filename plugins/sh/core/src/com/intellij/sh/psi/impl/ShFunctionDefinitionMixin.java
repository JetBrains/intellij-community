// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.ShFunctionDefinition;
import com.intellij.sh.psi.manipulator.ShElementGenerator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class ShFunctionDefinitionMixin extends ShCommandImpl implements ShFunctionDefinition {
  ShFunctionDefinitionMixin(ASTNode node) {
    super(node);
  }

  @Override
  public String getName() {
    PsiElement word = getWord();
    if (word != null) return word.getText();
    return null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    ASTNode functionNode = getNode();
    ASTNode identifierNode = functionNode.findChildByType(ShTypes.WORD);
    assert identifierNode != null : "Function identifier can't be empty";
    PsiElement newFunctionIdentifier = ShElementGenerator.createFunctionIdentifier(getProject(), name);
    functionNode.replaceChild(identifierNode, newFunctionIdentifier.getNode());
    return this;
  }

  @Override
  public @Nullable PsiElement getNameIdentifier() {
    return getWord();
  }

  @Override
  public int getTextOffset() {
    PsiElement word = getWord();
    return word != null ? word.getTextOffset() : super.getTextOffset();
  }
}