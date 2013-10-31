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
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author yole
 */
public class XmlHighlighterColors {
  private XmlHighlighterColors() { }

  public static final TextAttributesKey XML_PROLOGUE =
    TextAttributesKey.createTextAttributesKey("XML_PROLOGUE", HighlighterColors.TEXT);
  public static final TextAttributesKey XML_COMMENT =
    TextAttributesKey.createTextAttributesKey("XML_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
  public static final TextAttributesKey XML_TAG =
    TextAttributesKey.createTextAttributesKey("XML_TAG", DefaultLanguageHighlighterColors.MARKUP_TAG);
  public static final TextAttributesKey XML_TAG_NAME =
    TextAttributesKey.createTextAttributesKey("XML_TAG_NAME", DefaultLanguageHighlighterColors.KEYWORD);
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
  public static final TextAttributesKey HTML_ATTRIBUTE_NAME =
    TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_NAME", DefaultLanguageHighlighterColors.MARKUP_ATTRIBUTE);
  public static final TextAttributesKey HTML_ATTRIBUTE_VALUE =
    TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_VALUE", DefaultLanguageHighlighterColors.STRING);
  public static final TextAttributesKey HTML_ENTITY_REFERENCE =
    TextAttributesKey.createTextAttributesKey("HTML_ENTITY_REFERENCE", DefaultLanguageHighlighterColors.MARKUP_ENTITY);

  public static final TextAttributesKey HTML_CODE =
    TextAttributesKey.createTextAttributesKey("HTML_CODE", HighlighterColors.TEXT);
}
