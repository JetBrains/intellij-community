/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.formatter.xml;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * Moved from CodeStyleSettings
 * @author Rustam Vishnyakov
 */
public class XmlCodeStyleSettings extends CustomCodeStyleSettings {

  public static final int WS_AROUND_CDATA_PRESERVE = 0;
  public static final int WS_AROUND_CDATA_NONE = 1;
  public static final int WS_AROUND_CDATA_NEW_LINES = 2;

  public boolean XML_KEEP_WHITESPACES = false;
  public int XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
  public int XML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

  public boolean XML_KEEP_LINE_BREAKS = true;
  public boolean XML_KEEP_LINE_BREAKS_IN_TEXT = true;
  public int XML_KEEP_BLANK_LINES = 2;

  public boolean XML_ALIGN_ATTRIBUTES = true;
  public boolean XML_ALIGN_TEXT = false;

  public boolean XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = false;
  public boolean XML_SPACE_AFTER_TAG_NAME = false;
  public boolean XML_SPACE_INSIDE_EMPTY_TAG = false;

  public boolean XML_KEEP_WHITE_SPACES_INSIDE_CDATA = false;
  public int XML_WHITE_SPACE_AROUND_CDATA = WS_AROUND_CDATA_PRESERVE;

  public boolean XML_LEGACY_SETTINGS_IMPORTED = false;

  public XmlCodeStyleSettings(CodeStyleSettings container) {
    super("XML", container);
  }

  @Override
  public void importLegacySettings() {
    if (!XML_LEGACY_SETTINGS_IMPORTED) {
      CodeStyleSettings container = getContainer();
      CodeStyleSettings defaults = new CodeStyleSettings();
      XML_KEEP_WHITESPACES = container.XML_KEEP_WHITESPACES;
      XML_ATTRIBUTE_WRAP = container.XML_ATTRIBUTE_WRAP;
      XML_TEXT_WRAP = container.XML_TEXT_WRAP;
      XML_KEEP_LINE_BREAKS = container.XML_KEEP_LINE_BREAKS;
      XML_KEEP_LINE_BREAKS_IN_TEXT = container.XML_KEEP_LINE_BREAKS_IN_TEXT;
      XML_KEEP_BLANK_LINES = container.XML_KEEP_BLANK_LINES;
      XML_ALIGN_ATTRIBUTES = container.XML_ALIGN_ATTRIBUTES;
      XML_ALIGN_TEXT = container.XML_ALIGN_TEXT;
      XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = container.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE;
      XML_SPACE_AFTER_TAG_NAME = container.XML_SPACE_AFTER_TAG_NAME;
      XML_SPACE_INSIDE_EMPTY_TAG = container.XML_SPACE_INSIDE_EMPTY_TAG;
      XML_KEEP_WHITE_SPACES_INSIDE_CDATA = container.XML_KEEP_WHITE_SPACES_INSIDE_CDATA;
      XML_WHITE_SPACE_AROUND_CDATA = container.XML_WHITE_SPACE_AROUND_CDATA;

      container.XML_KEEP_WHITESPACES = defaults.XML_KEEP_WHITESPACES ;
      container.XML_ATTRIBUTE_WRAP = defaults.XML_ATTRIBUTE_WRAP;
      container.XML_TEXT_WRAP = defaults.XML_TEXT_WRAP;
      container.XML_KEEP_LINE_BREAKS = defaults.XML_KEEP_LINE_BREAKS;
      container.XML_KEEP_LINE_BREAKS_IN_TEXT = defaults.XML_KEEP_LINE_BREAKS_IN_TEXT;
      container.XML_KEEP_BLANK_LINES = defaults.XML_KEEP_BLANK_LINES;
      container.XML_ALIGN_ATTRIBUTES = defaults.XML_ALIGN_ATTRIBUTES;
      container.XML_ALIGN_TEXT = defaults.XML_ALIGN_TEXT;
      container.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = defaults.XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE;
      container.XML_SPACE_AFTER_TAG_NAME = defaults.XML_SPACE_AFTER_TAG_NAME;
      container.XML_SPACE_INSIDE_EMPTY_TAG = defaults.XML_SPACE_INSIDE_EMPTY_TAG;
      container.XML_KEEP_WHITE_SPACES_INSIDE_CDATA = defaults.XML_KEEP_WHITE_SPACES_INSIDE_CDATA;
      container.XML_WHITE_SPACE_AROUND_CDATA = defaults.XML_WHITE_SPACE_AROUND_CDATA;

      XML_LEGACY_SETTINGS_IMPORTED = true;
    }
  }
}
