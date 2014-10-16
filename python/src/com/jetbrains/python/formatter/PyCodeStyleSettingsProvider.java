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
package com.jetbrains.python.formatter;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.intellij.psi.codeStyle.DisplayPriority;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new PyCodeStyleSettings(settings);
  }

  @NotNull
  @Override
  public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, "Python") {
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new PyCodeStyleMainPanel(getCurrentSettings(), settings);
      }

      public String getHelpTopic() {
        return "reference.settingsdialog.codestyle.python";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return "Python";
  }

  @Override
  public DisplayPriority getPriority() {
    return PlatformUtils.isPyCharm() ? DisplayPriority.KEY_LANGUAGE_SETTINGS : DisplayPriority.LANGUAGE_SETTINGS;
  }
}
