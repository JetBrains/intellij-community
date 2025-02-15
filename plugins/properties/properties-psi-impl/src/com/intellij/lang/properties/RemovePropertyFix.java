// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.Property;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class RemovePropertyFix extends PsiUpdateModCommandAction<Property> {
  RemovePropertyFix(final @NotNull Property origProperty) {
    super(origProperty);
  }

  @Override
  public @NotNull String getFamilyName() {
    return PropertiesBundle.message("remove.property.intention.text");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull Property property, @NotNull ModPsiUpdater updater) {
    property.delete();
  }
}
