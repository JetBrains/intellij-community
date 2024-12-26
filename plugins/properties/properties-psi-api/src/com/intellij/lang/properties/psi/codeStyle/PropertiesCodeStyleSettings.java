// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.psi.codeStyle;

import com.intellij.configurationStore.Property;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jdom.Element;

/**
 * @author Dmitry Batkovich
 */
public class PropertiesCodeStyleSettings extends CustomCodeStyleSettings {
  public static final char[] DELIMITERS = new char[]{'=', ':', ' '};

  public PropertiesCodeStyleSettings(CodeStyleSettings container) {
    super(PropertiesLanguage.INSTANCE.getID(), container);
  }

  public static PropertiesCodeStyleSettings getInstance(final Project project) {
    return CodeStyleSettingsManager.getSettings(project).getCustomSettings(PropertiesCodeStyleSettings.class);
  }

  public boolean SPACES_AROUND_KEY_VALUE_DELIMITER;
  public boolean KEEP_BLANK_LINES;

  @Property(externalName = "key_value_delimiter")
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
      for (final Element e : parentElement.getChildren("option")) {
        String fieldName = e.getAttributeValue("name");
        if ("KEY_VALUE_DELIMITER".equals(fieldName)) {
          final String value = e.getAttributeValue("value");
          delimiter = value.charAt(0);
          break;
        }
      }
      if (delimiter != null) {
        switch (delimiter) {
          case '=' -> KEY_VALUE_DELIMITER_CODE = 0;
          case ':' -> KEY_VALUE_DELIMITER_CODE = 1;
          case ' ' -> KEY_VALUE_DELIMITER_CODE = 2;
        }
      }
    }
  }

}
