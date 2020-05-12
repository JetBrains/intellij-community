// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.psi.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.MagicIntegerConstAccessor;
import com.intellij.lang.Language;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.psi.codeStyle.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

final class PropertiesLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public CodeStyleConfigurable createConfigurable(@NotNull CodeStyleSettings baseSettings,
                                                  @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(baseSettings, modelSettings, "Properties Files") {
      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.properties";
      }

      @Override
      protected CodeStyleAbstractPanel createPanel(CodeStyleSettings settings) {
        return new PropertiesCodeStyleSettingsPanel(settings);
      }
    };
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new PropertiesCodeStyleSettings(settings);
  }

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
  public CodeStyleFieldAccessor getAccessor(@NotNull Object codeStyleObject,
                                            @NotNull Field field) {
    if (codeStyleObject instanceof PropertiesCodeStyleSettings) {
      if ("KEY_VALUE_DELIMITER_CODE".equals(field.getName())) {
        return new MagicIntegerConstAccessor(
          codeStyleObject, field,
          new int[] {0, 1, 2},
          new String[] {"equals", "colon", "space"}
        );
      }
    }
    return super.getAccessor(codeStyleObject, field);
  }
}
