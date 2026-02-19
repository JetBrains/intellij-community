// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

final class DomNameSuggestionProvider implements NameSuggestionProvider {
  @Override
  public SuggestedNameInfo getSuggestedNames(final PsiElement element, final PsiElement nameSuggestionContext, final Set<String> result) {
    if (element instanceof PsiMetaOwner) {
      final PsiMetaData psiMetaData = ((PsiMetaOwner)element).getMetaData();
      if (psiMetaData instanceof DomMetaData domMetaData) {
        final GenericDomValue value = DomMetaData.getNameElement(domMetaData, domMetaData.getElement());
        ContainerUtil.addIfNotNull(result, getNameFromNameValue(value, true));
      }
    }
    return null;
  }

  private static @Nullable String getNameFromNameValue(final Object o, final boolean local) {
    if (o == null || o instanceof String) {
      return (String)o;
    }
    else if (o instanceof GenericValue value) {
      if (!local) {
        final Object name = value.getValue();
        if (name != null) {
          return String.valueOf(name);
        }
      }
      return value.getStringValue();
    }
    else {
      return String.valueOf(o);
    }
  }
}
