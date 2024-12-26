// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.*;
import com.intellij.sh.ShLanguage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings settings, @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(settings, modelSettings, getLanguage().getID()) {
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(final @NotNull CodeStyleSettings settings) {
        return new ShCodeStylePanel(settings, modelSettings);
      }
    };
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.INDENT_SETTINGS) {
      consumer.showStandardOptions("INDENT_SIZE", "USE_TAB_CHARACTER", "TAB_SIZE");
    }
  }

  @Override
  public @NotNull String getCodeSample(@NotNull SettingsType settingsType) {
    return "";
  }

  @Override
  public @NotNull Language getLanguage() {
    return ShLanguage.INSTANCE;
  }

  @Override
  public @NonNls @NotNull String getExternalLanguageId() {
    return "shell";
  }

  @Override
  public @Nullable CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new ShCodeStyleSettings(settings);
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.INDENT_SIZE = 2;
    indentOptions.TAB_SIZE = 2;
  }
}
