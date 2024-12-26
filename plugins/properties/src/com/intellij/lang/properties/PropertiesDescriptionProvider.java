// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteTypeDescriptionLocation;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewLongNameLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PropertiesDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(final @NotNull PsiElement element, final @Nullable ElementDescriptionLocation location) {
    if (element instanceof IProperty) {
      if (location instanceof DeleteTypeDescriptionLocation) {
        int count = ((DeleteTypeDescriptionLocation) location).isPlural() ? 2 : 1;
        return IdeBundle.message("prompt.delete.property", count);
      }
      if (location instanceof UsageViewLongNameLocation) {
        return ((IProperty) element).getKey();
      }
    }
    return null;
  }
}
