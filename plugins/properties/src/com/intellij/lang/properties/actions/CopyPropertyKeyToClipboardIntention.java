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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CopyPropertyKeyToClipboardIntention implements ModCommandAction {

  @Override
  public @NotNull String getFamilyName() {
    return PropertiesBundle.message("copy.property.key.to.clipboard.intention.family.name");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!context.file().getLanguage().isKindOf(PropertiesLanguage.INSTANCE) ||
        CopyPropertyValueToClipboardIntention.getProperty(context) == null) {
      return null;
    }
    return Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.LOW);
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    final Property property = CopyPropertyValueToClipboardIntention.getProperty(context);
    if (property == null) return ModCommand.nop();
    final String key = property.getUnescapedKey();
    if (key == null) return ModCommand.nop();
    return ModCommand.copyToClipboard(key);
  }
}