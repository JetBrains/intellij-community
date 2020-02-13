// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.psi.impl;

import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.sh.ShSupport;
import com.intellij.sh.ShTypes;
import com.intellij.sh.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShPsiImplUtil {
  static int getTextOffset(ShAssignmentCommand cmd) {
    return cmd.getLiteral().getNode().getStartOffset();
  }

  @Nullable
  static String getName(ShAssignmentCommand o) {
    PsiElement nameIdentifier = o.getNameIdentifier();
    return nameIdentifier == null ? null : nameIdentifier.getText();
  }

  static PsiElement setName(@NotNull ShAssignmentCommand o, String name) {
    ElementManipulator<ShAssignmentCommand> manipulator = ElementManipulators.getManipulator(o);
    if (manipulator != null) {
      return manipulator.handleContentChange(o, name);
    }
    throw new IncorrectOperationException();
  }

  static PsiElement getNameIdentifier(@NotNull ShAssignmentCommand o) {
    return o.getLiteral();
  }

  static int getTextOffset(@NotNull ShAssignmentExpression o) {
    return o.getLeft().getNode().getStartOffset();
  }

  static PsiElement setName(@NotNull ShAssignmentExpression o, String name) {
    ElementManipulator<ShAssignmentExpression> manipulator = ElementManipulators.getManipulator(o);
    if (manipulator != null) {
      return manipulator.handleContentChange(o, name);
    }
    throw new IncorrectOperationException();
  }

  @Nullable
  static String getName(@NotNull ShAssignmentExpression o) {
    PsiElement nameIdentifier = o.getNameIdentifier();
    return nameIdentifier == null ? null : nameIdentifier.getText();
  }

  /**
   * Retrieve the name identifier of the assignment expression.
   * An identifier on the left-hand-side is only valid,
   * if it contains a single leaf element with element type WORD.
   *
   * @param o the current expression
   * @return the name identifier of the assignment expression, if available
   */
  @Nullable
  static PsiElement getNameIdentifier(@NotNull ShAssignmentExpression o) {
    PsiElement left = o.getLeft();
    if (left instanceof ShLiteralExpression) {
      PsiElement first = left.getFirstChild();
      if (first instanceof LeafPsiElement
          && first.getNextSibling() == null
          && first.getNode().getElementType() == ShTypes.WORD) {
        return left;
      }
    }

    return null;
  }

  static int getTextOffset(@NotNull ShFunctionDefinition o) {
    PsiElement word = o.getWord();
    return word == null ? o.getNode().getStartOffset() : word.getNode().getStartOffset();
  }

  static PsiElement setName(@NotNull ShFunctionDefinition o, String name) {
    ElementManipulator<ShFunctionDefinition> manipulator = ElementManipulators.getManipulator(o);
    if (manipulator != null) {
      return manipulator.handleContentChange(o, name);
    }
    throw new IncorrectOperationException();
  }

  @Nullable
  static String getName(@NotNull ShFunctionDefinition o) {
    PsiElement word = o.getNameIdentifier();
    return word == null ? null : word.getText();
  }

  @Nullable
  static PsiElement getNameIdentifier(@NotNull ShFunctionDefinition o) {
    return o.getWord();
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShLiteralExpression o) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(o);
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShLiteral o) {
    return o instanceof ShString || o.getWord() != null
           ? ReferenceProvidersRegistry.getReferencesFromProviders(o)
           : PsiReference.EMPTY_ARRAY;
  }

  static PsiReference @NotNull [] getReferences(@NotNull ShVariable o) {
    return ShSupport.getInstance().getVariableReferences(o);
  }
}
