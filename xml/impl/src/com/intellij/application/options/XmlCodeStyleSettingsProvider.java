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

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlCodeStyleSettings;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated Methods moved to {@link XmlLanguageCodeStyleSettingsProvider}
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
public class XmlCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  @NotNull
  public Configurable createSettingsPage(final CodeStyleSettings settings, final CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, XmlBundle.message("title.xml")){
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
  public String getConfigurableDisplayName() {
    return getConfigurableDisplayNameText();
  }

  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new XmlCodeStyleSettings(settings);
  }

  public static @ConfigurableName String getConfigurableDisplayNameText() {
    return XmlBundle.message("title.xml");
  }
}
