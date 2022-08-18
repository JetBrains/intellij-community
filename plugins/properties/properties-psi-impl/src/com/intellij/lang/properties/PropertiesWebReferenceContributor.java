// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.paths.GlobalPathReferenceProvider;
import com.intellij.openapi.paths.WebReference;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

final class PropertiesWebReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(psiElement(PropertyValueImpl.class), new PsiReferenceProvider() {
      @Override
      public boolean acceptsTarget(@NotNull PsiElement target) {
        return false; // web references do not point to any real PsiElement
      }

      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!(element instanceof PropertyValueImpl)) return PsiReference.EMPTY_ARRAY;
        if (!element.textContains(':')) return PsiReference.EMPTY_ARRAY;

        PropertyValueImpl propertyValue = (PropertyValueImpl)element;
        String textValue = propertyValue.getText();

        if (GlobalPathReferenceProvider.isWebReferenceUrl(textValue)) {
          return new PsiReference[]{new WebReference(element, textValue)};
        }

        return PsiReference.EMPTY_ARRAY;
      }
    }, PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
