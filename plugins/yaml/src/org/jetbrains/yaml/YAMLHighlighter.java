package org.jetbrains.yaml;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NonNls;

/**
 * @author oleg
 */
public class YAMLHighlighter {
  @NonNls
  static final String SCALAR_KEY_ID = "YAML_SCALAR_KEY";
  @NonNls
  static final String SCALAR_TEXT_ID = "YAML_SCALAR_VALUE";
  @NonNls
  static final String SCALAR_STRING_ID = "YAML_SCALAR_STRING";
  @NonNls
  static final String SCALAR_DSTRING_ID = "YAML_SCALAR_DSTRING";
  @NonNls
  static final String SCALAR_LIST_ID = "YAML_SCALAR_LIST";
  @NonNls
  static final String COMMENT_ID = "YAML_COMMENT";
  @NonNls
  static final String TEXT_ID = "YAML_TEXT";
  @NonNls
  static final String SIGN_ID = "YAML_SIGN";

  // Text default attrs
  public static final TextAttributesKey SCALAR_KEY_DEFAULT_ATTRS = DefaultLanguageHighlighterColors.KEYWORD;
  public static final TextAttributesKey COMMENT_DEFAULT_ATTRS = DefaultLanguageHighlighterColors.DOC_COMMENT;
  public static final TextAttributesKey SCALAR_TEXT_DEFAULT_ATTRS = HighlighterColors.TEXT;
  public static final TextAttributesKey SCALAR_STRING_DEFAULT_ATTRS = DefaultLanguageHighlighterColors.STRING;
  public static final TextAttributesKey SCALAR_DSTRING_DEFAULT_ATTRS = DefaultLanguageHighlighterColors.STRING;
  public static final TextAttributesKey SCALAR_LIST_DEFAULT_ATTRS = HighlighterColors.TEXT;
  public static final TextAttributesKey TEXT_DEFAULT_ATTRS = HighlighterColors.TEXT;
  public static final TextAttributesKey SIGN_DEFAULT_ATTRS = DefaultLanguageHighlighterColors.OPERATION_SIGN;

  // text attributes keys
  public static final TextAttributesKey SCALAR_KEY = TextAttributesKey
    .createTextAttributesKey(SCALAR_KEY_ID, SCALAR_KEY_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_TEXT = TextAttributesKey
    .createTextAttributesKey(SCALAR_TEXT_ID, SCALAR_TEXT_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_STRING =
      TextAttributesKey.createTextAttributesKey(SCALAR_STRING_ID, SCALAR_STRING_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_DSTRING =
      TextAttributesKey.createTextAttributesKey(SCALAR_DSTRING_ID, SCALAR_DSTRING_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_LIST = TextAttributesKey
    .createTextAttributesKey(SCALAR_LIST_ID, SCALAR_LIST_DEFAULT_ATTRS);
  public static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey(COMMENT_ID, COMMENT_DEFAULT_ATTRS);
  public static final TextAttributesKey TEXT = TextAttributesKey.createTextAttributesKey(TEXT_ID, TEXT_DEFAULT_ATTRS);
  public static final TextAttributesKey SIGN = TextAttributesKey.createTextAttributesKey(SIGN_ID, SIGN_DEFAULT_ATTRS);

  private YAMLHighlighter() {
  }
}
