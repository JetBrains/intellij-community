// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;


public final class XmlHighlighterColors {
  private XmlHighlighterColors() { }

  public static final TextAttributesKey XML_PROLOGUE =
    TextAttributesKey.createTextAttributesKey("XML_PROLOGUE", HighlighterColors.TEXT);
  public static final TextAttributesKey XML_COMMENT =
    TextAttributesKey.createTextAttributesKey("XML_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static final TextAttributesKey XML_TAG =
    TextAttributesKey.createTextAttributesKey("XML_TAG", DefaultLanguageHighlighterColors.MARKUP_TAG);
  public static final TextAttributesKey XML_TAG_NAME =
    TextAttributesKey.createTextAttributesKey("XML_TAG_NAME", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey XML_CUSTOM_TAG_NAME =
    TextAttributesKey.createTextAttributesKey("XML_CUSTOM_TAG_NAME", XML_TAG_NAME);
  public static final TextAttributesKey XML_NS_PREFIX =
    TextAttributesKey.createTextAttributesKey("XML_NS_PREFIX", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
  public static final TextAttributesKey XML_ATTRIBUTE_NAME =
    TextAttributesKey.createTextAttributesKey("XML_ATTRIBUTE_NAME", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE);
  public static final TextAttributesKey XML_ATTRIBUTE_VALUE =
    TextAttributesKey.createTextAttributesKey("XML_ATTRIBUTE_VALUE", DefaultLanguageHighlighterColors.STRING);
  public static final TextAttributesKey XML_TAG_DATA =
    TextAttributesKey.createTextAttributesKey("XML_TAG_DATA", HighlighterColors.TEXT);
  public static final TextAttributesKey XML_ENTITY_REFERENCE =
    TextAttributesKey.createTextAttributesKey("XML_ENTITY_REFERENCE", DefaultLanguageHighlighterColors.MARKUP_ENTITY);

  public static final TextAttributesKey HTML_COMMENT =
    TextAttributesKey.createTextAttributesKey("HTML_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static final TextAttributesKey HTML_TAG =
    TextAttributesKey.createTextAttributesKey("HTML_TAG", DefaultLanguageHighlighterColors.MARKUP_TAG);

  public static final TextAttributesKey HTML_TAG_NAME =
    TextAttributesKey.createTextAttributesKey("HTML_TAG_NAME", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey HTML_CUSTOM_TAG_NAME =
    TextAttributesKey.createTextAttributesKey("HTML_CUSTOM_TAG_NAME", HTML_TAG_NAME);


  public static final TextAttributesKey HTML_ATTRIBUTE_NAME =
    TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_NAME", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE);
  public static final TextAttributesKey HTML_ATTRIBUTE_VALUE =
    TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_VALUE", DefaultLanguageHighlighterColors.STRING);
  public static final TextAttributesKey HTML_ENTITY_REFERENCE =
    TextAttributesKey.createTextAttributesKey("HTML_ENTITY_REFERENCE", DefaultLanguageHighlighterColors.MARKUP_ENTITY);

  public static final TextAttributesKey HTML_CODE =
    TextAttributesKey.createTextAttributesKey("HTML_CODE", HighlighterColors.TEXT);

  public static final TextAttributesKey XML_INJECTED_LANGUAGE_FRAGMENT =
    EditorColors.createInjectedLanguageFragmentKey(XMLLanguage.INSTANCE);
  public static final TextAttributesKey HTML_INJECTED_LANGUAGE_FRAGMENT =
    EditorColors.createInjectedLanguageFragmentKey(HTMLLanguage.INSTANCE);

  public static final TextAttributesKey MATCHED_TAG_NAME =
    TextAttributesKey.createTextAttributesKey("MATCHED_TAG_NAME", CodeInsightColors.MATCHED_BRACE_ATTRIBUTES);
}
