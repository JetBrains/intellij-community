// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.dom.intentions;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class AddMavenDependencyQuickFixProvider extends UnresolvedReferenceQuickFixProvider<PsiJavaCodeReferenceElement> {
  @Override
  public void registerFixes(@NotNull PsiJavaCodeReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    if (!PsiUtil.isModuleFile(ref.getContainingFile())) {
      registrar.register(new AddMavenDependencyQuickFix(ref));
    }
  }

  @Override
  public @NotNull Class<PsiJavaCodeReferenceElement> getReferenceClass() {
    return PsiJavaCodeReferenceElement.class;
  }
}