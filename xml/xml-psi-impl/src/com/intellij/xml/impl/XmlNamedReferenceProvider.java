// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceHints;
import com.intellij.model.psi.PsiSymbolReferenceProvider;
import com.intellij.psi.xml.XmlNamedReferenceHost;
import com.intellij.util.SmartList;
import com.intellij.xml.XmlNamedReferenceProviderBean;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Internal
public final class XmlNamedReferenceProvider implements PsiSymbolReferenceProvider {

  @NotNull
  @Override
  public Collection<? extends PsiSymbolReference> getReferences(@NotNull PsiExternalReferenceHost element,
                                                                @NotNull PsiSymbolReferenceHints hints) {
    if (!(element instanceof XmlNamedReferenceHost)) {
      return Collections.emptyList();
    }
    final XmlNamedReferenceHost host = (XmlNamedReferenceHost)element;
    final Collection<XmlNamedReferenceProviderBean> beans = NamedReferenceProviders.getInstance().getNamedReferenceProviderBeans(host);
    final List<PsiSymbolReference> result = new SmartList<>();
    for (XmlNamedReferenceProviderBean bean : beans) {
      result.addAll(bean.getInstance().getReferences(element, hints));
    }
    return result;
  }
}
