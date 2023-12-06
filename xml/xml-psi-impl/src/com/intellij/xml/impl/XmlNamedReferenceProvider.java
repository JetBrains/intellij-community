// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceHints;
import com.intellij.model.psi.PsiSymbolReferenceProvider;
import com.intellij.model.search.SearchRequest;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlNamedReferenceHost;
import com.intellij.util.SmartList;
import com.intellij.xml.XmlNamedReferenceProviderBean;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Internal
public final class XmlNamedReferenceProvider implements PsiSymbolReferenceProvider {

  @Override
  public @NotNull Collection<? extends PsiSymbolReference> getReferences(@NotNull PsiExternalReferenceHost element,
                                                                         @NotNull PsiSymbolReferenceHints hints) {
    if (!(element instanceof XmlNamedReferenceHost host)) {
      return Collections.emptyList();
    }
    final Collection<XmlNamedReferenceProviderBean> beans = NamedReferenceProviders.getInstance().getNamedReferenceProviderBeans(host);
    final List<PsiSymbolReference> result = new SmartList<>();
    for (XmlNamedReferenceProviderBean bean : beans) {
      result.addAll(bean.getInstance().getReferences(element, hints));
    }
    return result;
  }

  @Override
  public @NotNull Collection<? extends @NotNull SearchRequest> getSearchRequests(@NotNull Project project, @NotNull Symbol target) {
    List<SearchRequest> result = new ArrayList<>();
    for (PsiSymbolReferenceProvider provider : NamedReferenceProviders.getInstance().getNamedReferenceProviders(target)) {
      result.addAll(provider.getSearchRequests(project, target));
    }
    return result;
  }
}
