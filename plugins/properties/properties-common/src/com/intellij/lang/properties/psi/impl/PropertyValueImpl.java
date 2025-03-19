// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi.impl;

import com.intellij.psi.HintedReferenceHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PropertyValueImpl extends LeafPsiElement implements HintedReferenceHost {
  public PropertyValueImpl(@NotNull IElementType type, CharSequence text) {
    super(type, text);
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return getReferences(PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public PsiReference @NotNull [] getReferences(PsiReferenceService.@NotNull Hints hints) {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, hints);
  }

  @Override
  public boolean shouldAskParentForReferences(PsiReferenceService.@NotNull Hints hints) {
    return false;
  }

  @Override
  public PsiReference getReference() {
    PsiReference[] references = getReferences();
    return references.length == 0 ? null : references[0];
  }

  @Override
  public @NonNls String toString() {
    return "PropertyValueImpl: " + getText();
  }
}
