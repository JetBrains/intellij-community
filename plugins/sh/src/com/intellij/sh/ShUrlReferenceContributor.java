// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh;

import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.ArbitraryPlaceUrlReferenceProvider;
import com.intellij.sh.psi.ShLiteral;
import org.jetbrains.annotations.NotNull;

public class ShUrlReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(new PsiElementPattern.Capture<ShLiteral>(ShLiteral.class) {},
                                        new ArbitraryPlaceUrlReferenceProvider(), PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
