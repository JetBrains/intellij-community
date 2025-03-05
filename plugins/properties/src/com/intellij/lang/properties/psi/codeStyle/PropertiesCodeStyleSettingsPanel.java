// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi.codeStyle;

import com.intellij.application.options.codeStyle.OptionTableWithPreviewPanel;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesBundle;
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
    addOption("ALIGN_GROUP_FIELD_DECLARATIONS", PropertiesBundle.message("align.properties.in.column.code.style.option"));
  }

  @Override
  public @Nullable Language getDefaultLanguage() {
    return PropertiesLanguage.INSTANCE;
  }
}
