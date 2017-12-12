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
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesCodeStyleSettings extends CustomCodeStyleSettings {
  public final static char[] DELIMITERS = new char[]{'=', ':', ' '};

  public PropertiesCodeStyleSettings(CodeStyleSettings container) {
    super(PropertiesLanguage.INSTANCE.getID(), container);
  }

  public static PropertiesCodeStyleSettings getInstance(final Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCustomSettings(PropertiesCodeStyleSettings.class);
  }

  public boolean SPACES_AROUND_KEY_VALUE_DELIMITER;
  public boolean KEEP_BLANK_LINES;
  public int KEY_VALUE_DELIMITER_CODE;

  public char getDelimiter() {
    return DELIMITERS[KEY_VALUE_DELIMITER_CODE];
  }

  @Override
  public void readExternal(Element parentElement) throws InvalidDataException {
    super.readExternal(parentElement);
    parentElement = parentElement.getChild(getTagName());
    if (parentElement != null) {
      Character delimiter = null;
      for (final Object o : parentElement.getChildren("option")) {
        Element e = (Element)o;
        String fieldName = e.getAttributeValue("name");
        if ("KEY_VALUE_DELIMITER".equals(fieldName)) {
          final String value = e.getAttributeValue("value");
          delimiter = value.charAt(0);
          break;
        }
      }
      if (delimiter != null) {
        switch (delimiter) {
          case '=':
            KEY_VALUE_DELIMITER_CODE = 0;
            break;
          case ':':
            KEY_VALUE_DELIMITER_CODE = 1;
            break;
          case ' ':
            KEY_VALUE_DELIMITER_CODE = 2;
            break;
        }
      }
    }
  }

  @Override
  protected void importLegacySettings(@NotNull CodeStyleSettings rootSettings) {
    if (rootSettings.ALIGN_GROUP_FIELD_DECLARATIONS) {
      rootSettings.getCommonSettings(PropertiesLanguage.INSTANCE).ALIGN_GROUP_FIELD_DECLARATIONS = true;
    }
  }
}
