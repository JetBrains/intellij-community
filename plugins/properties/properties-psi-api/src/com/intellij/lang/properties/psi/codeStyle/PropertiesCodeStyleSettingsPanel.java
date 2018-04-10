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
package com.intellij.lang.properties.psi.codeStyle;

import com.intellij.application.options.codeStyle.OptionTableWithPreviewPanel;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesCodeStyleSettingsPanel extends OptionTableWithPreviewPanel {
  public PropertiesCodeStyleSettingsPanel(CodeStyleSettings settings) {
    super(settings);
    init();
  }

  @Override
  public LanguageCodeStyleSettingsProvider.SettingsType getSettingsType() {
    return LanguageCodeStyleSettingsProvider.SettingsType.BLANK_LINES_SETTINGS;
  }

  @Override
  protected void initTables() {
    addOption("ALIGN_GROUP_FIELD_DECLARATIONS", "Align properties in column");
  }

  @Nullable
  @Override
  public Language getDefaultLanguage() {
    return PropertiesLanguage.INSTANCE;
  }
}
