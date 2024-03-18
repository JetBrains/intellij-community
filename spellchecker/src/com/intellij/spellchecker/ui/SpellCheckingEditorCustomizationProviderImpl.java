// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.ui;

import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class SpellCheckingEditorCustomizationProviderImpl extends SpellCheckingEditorCustomizationProvider {
  private static final NotNullLazyValue<SpellCheckingEditorCustomization> ENABLED =
    NotNullLazyValue.createValue(() -> new SpellCheckingEditorCustomization(true));
  private static final NotNullLazyValue<SpellCheckingEditorCustomization> DISABLED =
    NotNullLazyValue.createValue(() -> new SpellCheckingEditorCustomization(false));

  @Override
  public @NotNull EditorCustomization getEnabledCustomization() {
    return ENABLED.getValue();
  }

  @Override
  public @NotNull EditorCustomization getDisabledCustomization() {
    return DISABLED.getValue();
  }

  @Override
  public Set<String> getSpellCheckingToolNames() {
    return SpellCheckingEditorCustomization.getSpellCheckingToolNames();
  }
}
