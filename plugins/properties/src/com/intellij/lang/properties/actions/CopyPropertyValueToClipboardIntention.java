// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.actions;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.lang.properties.psi.Property;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class CopyPropertyValueToClipboardIntention implements ModCommandAction {
  @Override
  public @NotNull String getFamilyName() {
    return PropertiesBundle.message("copy.property.value.to.clipboard.intention.family.name");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!context.file().getLanguage().isKindOf(PropertiesLanguage.INSTANCE) ||
        getProperty(context) == null) {
      return null;
    }
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.LOW);
  }

  @VisibleForTesting
  public static @Nullable Property getProperty(@NotNull ActionContext context) {
    return PsiTreeUtil.getParentOfType(context.findLeaf(), Property.class);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    final Property property = CopyPropertyValueToClipboardIntention.getProperty(context);
    if (property == null) return ModCommand.nop();
    final String value = property.getUnescapedValue();
    if (value == null) return ModCommand.nop();
    return ModCommand.copyToClipboard(value);
  }
}