/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class HtmlCodeStyleSettingsProvider extends CodeStyleSettingsProvider {

  public static final String DISPLAY_NAME = ApplicationBundle.message("title.html");

  @Override
  @NotNull
  public Configurable createSettingsPage(final CodeStyleSettings settings, final CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, DISPLAY_NAME) {
      @Override
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new HtmlCodeStyleMainPanel(settings, originalSettings);
      }

      @Override
      public String getHelpTopic() {
        return "reference.settingsdialog.IDE.globalcodestyle.html";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return DISPLAY_NAME;
  }

  @Nullable
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new HtmlCodeStyleSettings(settings);
  }
}
