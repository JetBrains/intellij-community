// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.magicLiteral;

import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesHandlerFactory;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.psi.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * Handles find usage for magic literals.
 * <strong>Install it</strong> as {@link FindUsagesHandlerFactory#EP_NAME}
 * @author Ilya.Kazakevich
 */
public final class PyMagicLiteralFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  @Override
  public boolean canFindUsages(final @NotNull PsiElement element) {
    return PyMagicLiteralTools.couldBeMagicLiteral(element);
  }

  @Override
  public @Nullable FindUsagesHandler createFindUsagesHandler(final @NotNull PsiElement element, final boolean forHighlightUsages) {
    return new MyFindUsagesHandler(element);
  }

  private static class MyFindUsagesHandler extends FindUsagesHandler {
    MyFindUsagesHandler(final PsiElement element) {
      super(element);
    }

    @Override
    protected @Nullable Collection<String> getStringsToSearch(final @NotNull PsiElement element) {
      return Collections.singleton(((StringLiteralExpression)element).getStringValue());
    }
  }
}
