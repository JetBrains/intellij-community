// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.MagicIntegerConstAccessor;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.xml.XmlCoreBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

public class XmlLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  public @NotNull CodeStyleConfigurable createConfigurable(final @NotNull CodeStyleSettings baseSettings,
                                                           final @NotNull CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(baseSettings, modelSettings, getConfigurableDisplayNameText()){
      @Override
      protected @NotNull CodeStyleAbstractPanel createPanel(final @NotNull CodeStyleSettings settings) {
        return new XmlCodeStyleMainPanel(getCurrentSettings(), settings);
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.IDE.globalcodestyle.xml";
      }
    };
  }

  @Override
  public CustomCodeStyleSettings createCustomSettings(@NotNull CodeStyleSettings settings) {
    return new XmlCodeStyleSettings(settings);
  }

  @Override
  public @NotNull Language getLanguage() {
    return XMLLanguage.INSTANCE;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.INDENT_SETTINGS) {
      return CodeStyleAbstractPanel.readFromFile(getClass(), "preview.xml.template");
    }
    return "";
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    customizeXml(consumer, settingsType);
  }

  static void customizeXml(@NotNull CodeStyleSettingsCustomizable consumer,
                                   @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("RIGHT_MARGIN", "WRAP_ON_TYPING");
    }
    if (settingsType == SettingsType.COMMENTER_SETTINGS) {
      consumer.showStandardOptions(CodeStyleSettingsCustomizable.CommenterOption.LINE_COMMENT_AT_FIRST_COLUMN.name(),
                                   CodeStyleSettingsCustomizable.CommenterOption.BLOCK_COMMENT_AT_FIRST_COLUMN.name(),
                                   CodeStyleSettingsCustomizable.CommenterOption.BLOCK_COMMENT_ADD_SPACE.name());
    }
  }

  @Override
  protected void customizeDefaults(@NotNull CommonCodeStyleSettings commonSettings,
                                   @NotNull CommonCodeStyleSettings.IndentOptions indentOptions) {
    commonSettings.setForceArrangeMenuAvailable(true);
    // HACK [yole]
    if (PlatformUtils.isRubyMine()) {
      indentOptions.INDENT_SIZE = 2;
    }
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  @Override
  public @Nullable CodeStyleFieldAccessor getAccessor(@NotNull Object codeStyleObject,
                                                      @NotNull Field field) {
    if (codeStyleObject instanceof XmlCodeStyleSettings) {
      if ("XML_WHITE_SPACE_AROUND_CDATA".equals(field.getName())) {
        return new MagicIntegerConstAccessor(
          codeStyleObject, field,
          new int[]{
            XmlCodeStyleSettings.WS_AROUND_CDATA_PRESERVE,
            XmlCodeStyleSettings.WS_AROUND_CDATA_NONE,
            XmlCodeStyleSettings.WS_AROUND_CDATA_NEW_LINES
          },
          new String[]{
            "preserve",
            "none",
            "new_lines"
          });
      }
      else if ("XML_TEXT_WRAP".equals(field.getName())) {
        return new MagicIntegerConstAccessor(
          codeStyleObject, field,
          new int[]{
            CommonCodeStyleSettings.DO_NOT_WRAP,
            CommonCodeStyleSettings.WRAP_AS_NEEDED
          },
          new String[]{
            "off",
            "normal"
          });
      }
    }
    return super.getAccessor(codeStyleObject, field);
  }

  public static @ConfigurableName String getConfigurableDisplayNameText() {
    return XmlCoreBundle.message("title.xml");
  }
}
