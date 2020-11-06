// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

final class PropertiesColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS;

  static {
    ATTRS = new AttributesDescriptor[PropertiesHighlighter.DISPLAY_NAMES.size()];
    TextAttributesKey[] keys = PropertiesHighlighter.DISPLAY_NAMES.keySet().toArray(TextAttributesKey.EMPTY_ARRAY);
    for (int i = 0; i < keys.length; i++) {
      TextAttributesKey key = keys[i];
      String name = PropertiesHighlighter.DISPLAY_NAMES.get(key).getFirst();
      ATTRS[i] = new AttributesDescriptor(name, key);
    }
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return OptionsBundle.message("properties.options.display.name");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.FileTypes.Properties;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    return new PropertiesHighlighter();
  }

  @Override
  @NotNull
  public String getDemoText() {
    return "# Comment on keys and values\n" +
           "key1=value1\n" +
           "! other values:\n" +
           "a\\=\\fb : x\\ty\\n\\x\\uzzzz\n"
      ;
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
