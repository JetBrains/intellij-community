// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteTypeDescriptionLocation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.StructureChangeDescriptionLocation;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewLongNameLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PropertiesDescriptionProvider implements ElementDescriptionProvider {
  @Override
  public String getElementDescription(final @NotNull PsiElement element, final @Nullable ElementDescriptionLocation location) {
    if (element instanceof IProperty property) {
      if (location instanceof StructureChangeDescriptionLocation) {
        return StringUtil.escapeLineBreak(StringUtil.notNullize(property.getKey()) + "=" + StringUtil.notNullize(property.getValue()));
      }
      if (location instanceof DeleteTypeDescriptionLocation) {
        int count = ((DeleteTypeDescriptionLocation) location).isPlural() ? 2 : 1;
        return IdeBundle.message("prompt.delete.property", count);
      }
      if (location instanceof UsageViewLongNameLocation) {
        return property.getKey();
      }
    }
    return null;
  }
}
