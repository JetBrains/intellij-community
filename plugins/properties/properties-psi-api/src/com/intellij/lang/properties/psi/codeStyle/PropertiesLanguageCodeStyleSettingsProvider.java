// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi.codeStyle;

import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertiesLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return PropertiesLanguage.INSTANCE;
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    consumer.showStandardOptions("ALIGN_GROUP_FIELD_DECLARATIONS");
    consumer.showCustomOption(PropertiesCodeStyleSettings.class, "SPACES_AROUND_KEY_VALUE_DELIMITER",
                              "Insert space around key-value delimiter", null);
    consumer.showCustomOption(PropertiesCodeStyleSettings.class,
                              "KEY_VALUE_DELIMITER_CODE",
                              "Key-value delimiter", null,
                              new String[]{"=", ":", "whitespace symbol"}, new int[]{0, 1, 2});
    consumer.showCustomOption(PropertiesCodeStyleSettings.class, "KEEP_BLANK_LINES",
                              "Keep blank lines", null);
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return "key1=value\n" +
           "some_key=some_value\n\n" +
           "#commentaries\n" +
           "last.key=some text here";
  }

  @Nullable
  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(getLanguage());
    defaultSettings.initIndentOptions();
    return defaultSettings;
  }
}
