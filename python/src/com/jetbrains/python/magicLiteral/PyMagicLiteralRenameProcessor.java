// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.magicLiteral;

import com.google.common.base.Preconditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supports renaming for magic literals.
 * <strong>Install it</strong> as {@link RenamePsiElementProcessor#EP_NAME}
 * @author Ilya.Kazakevich
 */
class PyMagicLiteralRenameProcessor extends RenamePsiElementProcessorBase {
  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return (PyMagicLiteralTools.couldBeMagicLiteral(element));
  }

  @Override
  public void renameElement(@NotNull final PsiElement element,
                            @NotNull final String newName,
                            final UsageInfo @NotNull [] usages,
                            @Nullable final RefactoringElementListener listener) {
    Preconditions.checkArgument(canProcessElement(element), "Element can't be renamed, call #canProcessElement first " + element);
    element.replace(PyElementGenerator.getInstance(element.getProject()).createStringLiteral((PyStringLiteralExpression)element, newName));
    for (final UsageInfo usage : usages) {
      final PsiReference reference = usage.getReference();
      if (reference != null) {
        reference.handleElementRename(newName);
      }
    }
  }
}
