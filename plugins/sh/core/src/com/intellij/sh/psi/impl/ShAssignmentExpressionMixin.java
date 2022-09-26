// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.ShAssignmentExpression;
import com.intellij.sh.psi.ShLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class ShAssignmentExpressionMixin extends ShBinaryExpressionImpl implements ShAssignmentExpression {
  ShAssignmentExpressionMixin(ASTNode node) {
    super(node);
  }

  @Override
  public int getTextOffset() {
    return getLeft().getNode().getStartOffset();
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    ElementManipulator<ShAssignmentExpression> manipulator = ElementManipulators.getManipulator(this);
    if (manipulator == null) return this;
    return manipulator.handleContentChange(this, name);
  }

  @Override
  @Nullable
  public String getName() {
    PsiElement nameIdentifier = getNameIdentifier();
    return nameIdentifier == null ? null : nameIdentifier.getText();
  }

  /**
   * Retrieve the name identifier of the assignment expression. An identifier on the left-hand-side is only valid,
   * if it contains a single leaf element with element type WORD.
   *
   * @return the name identifier of the assignment expression, if available
   */
  @Override
  @Nullable
  public PsiElement getNameIdentifier() {
    PsiElement left = getLeft();
    if (!(left instanceof ShLiteralExpression)) return null;
    PsiElement first = left.getFirstChild();
    if (first instanceof LeafPsiElement && first.getNextSibling() == null && first.getNode().getElementType() == ShTypes.WORD) return left;
    return null;
  }
}
