/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.lang.properties.psi.codeStyle;

import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesCodeStyleSettings extends CustomCodeStyleSettings {
  public final static char DEFAULT_KEY_VALUE_DELIMITER = '=';

  public PropertiesCodeStyleSettings(CodeStyleSettings container) {
    super(PropertiesLanguage.INSTANCE.getID(), container);
  }

  public static PropertiesCodeStyleSettings getInstance(final Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCustomSettings(PropertiesCodeStyleSettings.class);
  }

  public char KEY_VALUE_DELIMITER = DEFAULT_KEY_VALUE_DELIMITER;
}
