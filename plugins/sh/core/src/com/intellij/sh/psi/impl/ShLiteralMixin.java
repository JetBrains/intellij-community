// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.sh.psi.ShLiteral;
import com.intellij.sh.psi.ShString;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class ShLiteralMixin extends ShSimpleCommandElementImpl implements ShLiteral {
  ShLiteralMixin(ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable PsiElement getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    if (this instanceof ShString || this.getWord() != null) {
      ElementManipulator<ShLiteral> manipulator = ElementManipulators.getManipulator(this);
      if (manipulator != null) {
        return manipulator.handleContentChange(this, name);
      }
    }
    throw new IncorrectOperationException();
  }
}
