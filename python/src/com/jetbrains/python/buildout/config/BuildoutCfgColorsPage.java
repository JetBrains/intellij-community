// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author traff
 */
public class BuildoutCfgColorsPage implements ColorSettingsPage {
  private static final AttributesDescriptor[] ATTRS = new AttributesDescriptor[]{
    new AttributesDescriptor("Section name", BuildoutCfgSyntaxHighlighter.BUILDOUT_SECTION_NAME),
    new AttributesDescriptor("Key", BuildoutCfgSyntaxHighlighter.BUILDOUT_KEY),
    new AttributesDescriptor("Value", BuildoutCfgSyntaxHighlighter.BUILDOUT_VALUE),
    new AttributesDescriptor("Key value separator", BuildoutCfgSyntaxHighlighter.BUILDOUT_KEY_VALUE_SEPARATOR),
    new AttributesDescriptor("Comment", BuildoutCfgSyntaxHighlighter.BUILDOUT_COMMENT)
  };

  @NonNls private static final HashMap<String, TextAttributesKey> ourTagToDescriptorMap = new HashMap<>();

  static {
    //ourTagToDescriptorMap.put("comment", DjangoTemplateHighlighterColors.DJANGO_COMMENT);
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return "Buildout config";
  }

  @Override
  public Icon getIcon() {
    return BuildoutCfgFileType.INSTANCE.getIcon();
  }

  @Override
  @NotNull
  public AttributesDescriptor[] getAttributeDescriptors() {
    return ATTRS;
  }

  @Override
  @NotNull
  public ColorDescriptor[] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public SyntaxHighlighter getHighlighter() {
    final SyntaxHighlighter highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(BuildoutCfgFileType.INSTANCE, null, null);
    assert highlighter != null;
    return highlighter;
  }

  @Override
  @NotNull
  public String getDemoText() {
    return
      "; Buildout config\n"+
      "[buildout]\n" +
      "parts = python\n" +
      "develop = .\n" +
      "eggs = django-shorturls\n" +
      "\n" +
      "[python]\n" +
      "recipe = zc.recipe.egg\n" +
      "interpreter = python\n" +
      "eggs = ${buildout:eggs}";
  }

  @Override
  public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourTagToDescriptorMap;
  }
}
