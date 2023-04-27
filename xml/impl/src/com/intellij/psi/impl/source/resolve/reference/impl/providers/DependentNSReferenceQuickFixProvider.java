// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ManuallySetupExtResourceAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import org.jetbrains.annotations.NotNull;

final class DependentNSReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<DependentNSReference> {
  @Override
  public void registerFixes(@NotNull DependentNSReference ref, @NotNull QuickFixActionRegistrar registrar) {
    registrar.register(new FetchExtResourceAction(ref.isForceFetchResultValid()));
    registrar.register(new ManuallySetupExtResourceAction());
    registrar.register(new IgnoreExtResourceAction());
  }

  @NotNull
  @Override
  public Class<DependentNSReference> getReferenceClass() {
    return DependentNSReference.class;
  }
}
