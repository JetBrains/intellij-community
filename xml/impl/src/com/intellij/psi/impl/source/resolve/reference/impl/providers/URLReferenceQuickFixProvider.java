// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.impl.quickfix.AddXsiSchemaLocationForExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.IgnoreExtResourceAction;
import com.intellij.codeInsight.daemon.impl.quickfix.ManuallySetupExtResourceAction;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

final class URLReferenceQuickFixProvider extends UnresolvedReferenceQuickFixProvider<URLReference> {
  @Override
  public void registerFixes(@NotNull URLReference ref, @NotNull QuickFixActionRegistrar registrar) {
    registrar.register(new FetchExtResourceAction());
    registrar.register(new ManuallySetupExtResourceAction());
    registrar.register(new IgnoreExtResourceAction());

    final PsiElement parentElement = ref.getElement().getParent();
    if (parentElement instanceof XmlAttribute && ((XmlAttribute)parentElement).isNamespaceDeclaration()) {
      registrar.register(new AddXsiSchemaLocationForExtResourceAction());
    }
  }

  @NotNull
  @Override
  public Class<URLReference> getReferenceClass() {
    return URLReference.class;
  }
}
