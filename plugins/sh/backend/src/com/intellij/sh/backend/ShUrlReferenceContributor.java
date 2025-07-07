// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.backend;

import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.psi.impl.source.resolve.reference.CommentsReferenceContributor;
import com.intellij.sh.psi.ShLiteral;
import org.jetbrains.annotations.NotNull;

final class ShUrlReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    PsiReferenceProvider provider = CommentsReferenceContributor.COMMENTS_REFERENCE_PROVIDER_TYPE.getProvider();
    registrar.registerReferenceProvider(new PsiElementPattern.Capture<>(ShLiteral.class) {},
                                        provider, PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
