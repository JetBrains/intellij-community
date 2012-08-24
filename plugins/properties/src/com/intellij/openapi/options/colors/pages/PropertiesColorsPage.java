/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.options.colors.pages;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

public class PropertiesColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS;

  static {
    ATTRS = new AttributesDescriptor[PropertiesHighlighter.DISPLAY_NAMES.size()];
    TextAttributesKey[] keys = PropertiesHighlighter.DISPLAY_NAMES.keySet().toArray(new TextAttributesKey[0]);
    for (int i = 0; i < keys.length; i++) {
      TextAttributesKey key = keys[i];
      String name = PropertiesHighlighter.DISPLAY_NAMES.get(key).getFirst();
      ATTRS[i] = new AttributesDescriptor(name, key);
    }
  }

  @NotNull
  public String getDisplayName() {
    return OptionsBundle.message("properties.options.display.name");
  }

  public Icon getIcon() {
    return AllIcons.FileTypes.Properties;
  }

  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PropertiesHighlighter();
  }

  @NotNull
  public String getDemoText() {
    return "# Comment on keys and values\n" +
           "key1=value1\n" +
           "! other values:\n" +
           "a\\=\\fb : x\\ty\\n\\x\\uzzzz\n"
      ;
  }

  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
