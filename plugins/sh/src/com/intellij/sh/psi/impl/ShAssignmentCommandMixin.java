// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.sh.psi.ShAssignmentCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class ShAssignmentCommandMixin extends ShCommandImpl implements ShAssignmentCommand {
  ShAssignmentCommandMixin(ASTNode node) {
    super(node);
  }

  @Override
  public int getTextOffset() {
    return getLiteral().getNode().getStartOffset();
  }

  @Override
  @Nullable
  public String getName() {
    PsiElement nameIdentifier = getNameIdentifier();
    return nameIdentifier == null ? null : nameIdentifier.getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    ElementManipulator<ShAssignmentCommand> manipulator = ElementManipulators.getManipulator(this);
    if (manipulator == null) return this;
    return manipulator.handleContentChange(this, name);
  }

  @Override
  public PsiElement getNameIdentifier() {
    return getLiteral();
  }
}
