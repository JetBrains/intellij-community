/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.application.options.codeStyle.properties.CodeStyleFieldAccessor;
import com.intellij.application.options.codeStyle.properties.MagicIntegerConstAccessor;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.util.PlatformUtils;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * @author Rustam Vishnyakov
 */
public class XmlLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @Override
  @NotNull
  public CodeStyleConfigurable createConfigurable(@NotNull final CodeStyleSettings baseSettings,
                                                  @NotNull final CodeStyleSettings modelSettings) {
    return new CodeStyleAbstractConfigurable(baseSettings, modelSettings, getConfigurableDisplayNameText()){
      @Override
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new XmlCodeStyleMainPanel(getCurrentSettings(), settings);
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.IDE.globalcodestyle.xml";
      }
    };
  }

  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new XmlCodeStyleSettings(settings);
  }

  @NotNull
  @Override
  public Language getLanguage() {
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

  @Nullable
  @Override
  public CodeStyleFieldAccessor getAccessor(@NotNull Object codeStyleObject,
                                            @NotNull Field field) {
    if (codeStyleObject instanceof XmlCodeStyleSettings && "XML_WHITE_SPACE_AROUND_CDATA".equals(field.getName())) {
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
    return super.getAccessor(codeStyleObject, field);
  }

  public static @ConfigurableName String getConfigurableDisplayNameText() {
    return XmlBundle.message("title.xml");
  }
}
