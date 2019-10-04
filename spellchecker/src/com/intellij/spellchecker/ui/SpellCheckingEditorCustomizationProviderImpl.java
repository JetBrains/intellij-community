// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.ui;

import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author nik
 */
final class SpellCheckingEditorCustomizationProviderImpl extends SpellCheckingEditorCustomizationProvider {
  private static final NotNullLazyValue<SpellCheckingEditorCustomization> ENABLED = NotNullLazyValue.createValue(() -> new SpellCheckingEditorCustomization(true));
  private static final NotNullLazyValue<SpellCheckingEditorCustomization> DISABLED = NotNullLazyValue.createValue(() -> new SpellCheckingEditorCustomization(false));

  @NotNull
  @Override
  public EditorCustomization getEnabledCustomization() {
    return ENABLED.getValue();
  }

  @NotNull
  @Override
  public EditorCustomization getDisabledCustomization() {
    return DISABLED.getValue();
  }

  @Override
  public Set<String> getSpellCheckingToolNames() {
    return SpellCheckingEditorCustomization.getSpellCheckingToolNames();
  }
}
