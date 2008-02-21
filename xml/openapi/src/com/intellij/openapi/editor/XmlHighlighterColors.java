package com.intellij.openapi.editor;

import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author yole
 */
public class XmlHighlighterColors {
  private XmlHighlighterColors() {
  }

  public static final TextAttributesKey XML_PROLOGUE = TextAttributesKey.createTextAttributesKey("XML_PROLOGUE");
  public static final TextAttributesKey XML_COMMENT = TextAttributesKey.createTextAttributesKey("XML_COMMENT");
  public static final TextAttributesKey XML_TAG = TextAttributesKey.createTextAttributesKey("XML_TAG");
  public static final TextAttributesKey XML_TAG_NAME = TextAttributesKey.createTextAttributesKey("XML_TAG_NAME");
  public static final TextAttributesKey XML_ATTRIBUTE_NAME = TextAttributesKey.createTextAttributesKey("XML_ATTRIBUTE_NAME");
  public static final TextAttributesKey XML_ATTRIBUTE_VALUE = TextAttributesKey.createTextAttributesKey("XML_ATTRIBUTE_VALUE");
  public static final TextAttributesKey XML_TAG_DATA = TextAttributesKey.createTextAttributesKey("XML_TAG_DATA");
  public static final TextAttributesKey XML_ENTITY_REFERENCE = TextAttributesKey.createTextAttributesKey("XML_ENTITY_REFERENCE");

  public static final TextAttributesKey HTML_COMMENT = TextAttributesKey.createTextAttributesKey("HTML_COMMENT");
  public static final TextAttributesKey HTML_TAG = TextAttributesKey.createTextAttributesKey("HTML_TAG");
  public static final TextAttributesKey HTML_TAG_NAME = TextAttributesKey.createTextAttributesKey("HTML_TAG_NAME");
  public static final TextAttributesKey HTML_ATTRIBUTE_NAME = TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_NAME");
  public static final TextAttributesKey HTML_ATTRIBUTE_VALUE = TextAttributesKey.createTextAttributesKey("HTML_ATTRIBUTE_VALUE");
  public static final TextAttributesKey HTML_ENTITY_REFERENCE = TextAttributesKey.createTextAttributesKey("HTML_ENTITY_REFERENCE");
}
