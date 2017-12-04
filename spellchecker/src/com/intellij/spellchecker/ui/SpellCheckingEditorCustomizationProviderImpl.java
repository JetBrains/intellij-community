/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.spellchecker.ui;

import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author nik
 */
public class SpellCheckingEditorCustomizationProviderImpl extends SpellCheckingEditorCustomizationProvider {
  private static final SpellCheckingEditorCustomization ENABLED = new SpellCheckingEditorCustomization(true);
  private static final SpellCheckingEditorCustomization DISABLED = new SpellCheckingEditorCustomization(false);

  @Nullable
  @Override
  public EditorCustomization getEnabledCustomization() {
    return ENABLED;
  }

  @Nullable
  @Override
  public EditorCustomization getDisabledCustomization() {
    return DISABLED;
  }

  @Override
  public Set<String> getSpellCheckingToolNames() {
    return SpellCheckingEditorCustomization.getSpellCheckingToolNames();
  }
}
