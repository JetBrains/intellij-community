// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.icons.AllIcons;
import com.intellij.lang.properties.PropertiesHighlighter.PropertiesComponent;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;

final class PropertiesColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS;

  static {
    ATTRS = Arrays.stream(PropertiesComponent.values())
      .map(component -> new AttributesDescriptor(component.getMessagePointer(), component.getTextAttributesKey()))
      .toArray(AttributesDescriptor[]::new)
    ;
  }

  @Override
  public @NotNull String getDisplayName() {
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
  public @NotNull SyntaxHighlighter getHighlighter() {
    return new PropertiesHighlighterImpl();
  }

  @Override
  public @NotNull String getDemoText() {
    return """
      # This comment starts with '#'
      greetings=Hello
      ! This comment starts with '!'
      what\\=to\\=greet : \\'W\\o\\rld\\',\\tUniverse\\n\\uXXXX
      """
      ;
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return null;
  }
}
