/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Rustam Vishnyakov
 */
public class HtmlLanguageCodeStyleSettings extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return HTMLLanguage.INSTANCE;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return CodeStyleAbstractPanel.readFromFile(this.getClass(), "preview.html.template");
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
      consumer.showStandardOptions("RIGHT_MARGIN", "WRAP_ON_TYPING");
    }
  }

  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(HTMLLanguage.INSTANCE);
    defaultSettings.initIndentOptions();
    return defaultSettings;
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }
}
