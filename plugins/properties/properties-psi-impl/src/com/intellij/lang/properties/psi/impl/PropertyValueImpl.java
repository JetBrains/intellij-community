/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  @NonNls
  public String toString() {
    return "PropertyValueImpl: " + getText();
  }
}
