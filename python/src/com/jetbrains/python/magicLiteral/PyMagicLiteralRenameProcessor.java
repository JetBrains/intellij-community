/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.magicLiteral;

import com.google.common.base.Preconditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Supports renaming for magic literals.
 * <strong>Install it</strong> as {@link com.intellij.refactoring.rename.RenamePsiElementProcessor#EP_NAME}
 * @author Ilya.Kazakevich
 */
class PyMagicLiteralRenameProcessor extends RenamePsiElementProcessor {
  @Override
  public boolean canProcessElement(@NotNull final PsiElement element) {
    return (PyMagicLiteralTools.isMagicLiteral(element));
  }

  @Override
  public void renameElement(final PsiElement element,
                            final String newName,
                            final UsageInfo[] usages,
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
