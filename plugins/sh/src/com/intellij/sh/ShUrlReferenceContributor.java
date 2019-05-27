// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.ArbitraryPlaceUrlReferenceProvider;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.sh.psi.ShSimpleCommand;
import com.intellij.sh.psi.ShString;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShUrlReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(new PsiElementPattern.Capture<PsiElement>(PsiElement.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return (o instanceof ShString && PsiTreeUtil.getParentOfType((PsiElement) o, ShSimpleCommand.class) == null)
            || o instanceof ShSimpleCommand;
      }
    }, new ArbitraryPlaceUrlReferenceProvider(), PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
