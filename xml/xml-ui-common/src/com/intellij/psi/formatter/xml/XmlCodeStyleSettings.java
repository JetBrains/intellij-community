/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.configurationStore.Property;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.WrapConstant;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * Moved from CodeStyleSettings
 */
public class XmlCodeStyleSettings extends CustomCodeStyleSettings {

  public static final int WS_AROUND_CDATA_PRESERVE = 0;
  public static final int WS_AROUND_CDATA_NONE = 1;
  public static final int WS_AROUND_CDATA_NEW_LINES = 2;

  @Property(externalName = "keep_whitespaces")
  public boolean XML_KEEP_WHITESPACES = false;

  @Property(externalName = "attribute_wrap")
  @WrapConstant
  public int XML_ATTRIBUTE_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

  @Property(externalName = "text_wrap")
  public int XML_TEXT_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;

  @Property(externalName = "keep_line_breaks")
  public boolean XML_KEEP_LINE_BREAKS = true;

  @Property(externalName = "keep_line_breaks_in_text")
  public boolean XML_KEEP_LINE_BREAKS_IN_TEXT = true;

  @Property(externalName = "keep_blank_lines")
  public int XML_KEEP_BLANK_LINES = 2;

  @Property(externalName = "align_attributes")
  public boolean XML_ALIGN_ATTRIBUTES = true;

  @Property(externalName = "align_text")
  public boolean XML_ALIGN_TEXT = false;

  @Property(externalName = "space_around_equals_in_attribute")
  public boolean XML_SPACE_AROUND_EQUALITY_IN_ATTRIBUTE = false;

  @Property(externalName = "space_after_tag_name")
  public boolean XML_SPACE_AFTER_TAG_NAME = false;

  @Property(externalName = "space_inside_empty_tag")
  public boolean XML_SPACE_INSIDE_EMPTY_TAG = false;

  @Property(externalName = "keep_whitespaces_inside_cdata")
  public boolean XML_KEEP_WHITE_SPACES_INSIDE_CDATA = false;

  @Property(externalName = "keep_whitespaces_around_cdata")
  public int XML_WHITE_SPACE_AROUND_CDATA = WS_AROUND_CDATA_PRESERVE;

  public XmlCodeStyleSettings(CodeStyleSettings container) {
    super("XML", container);
  }

}
