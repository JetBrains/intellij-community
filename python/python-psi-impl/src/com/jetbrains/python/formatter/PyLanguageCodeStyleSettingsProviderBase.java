// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.formatter;

import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.*;
import com.jetbrains.python.PythonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PyLanguageCodeStyleSettingsProviderBase extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return PythonLanguage.getInstance();
  }

  @Override
  public @Nullable String getCodeSample(@NotNull SettingsType settingsType) {
    return null;
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    indentOptions.INDENT_SIZE = 4;
    commonSettings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
    commonSettings.KEEP_BLANK_LINES_IN_DECLARATIONS = 1;
    // Don't set it to 2 -- this setting is used implicitly in a lot of methods related to spacing,
    // e.g. in SpacingBuilder#blankLines(), and can lead to unexpected side-effects in formatter's
    // behavior
    commonSettings.KEEP_BLANK_LINES_IN_CODE = 1;
    commonSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    commonSettings.CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new PyCodeStyleSettings(settings);
  }

  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings baseSettings,
                                                           @NotNull CodeStyleSettings modelSettings) {
    return super.createConfigurable(baseSettings, modelSettings);
  }
}
