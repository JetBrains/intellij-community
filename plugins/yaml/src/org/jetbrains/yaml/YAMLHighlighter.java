package org.jetbrains.yaml;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.TextAttributesKeyDefaults;
import com.intellij.openapi.editor.markup.TextAttributes;
import org.jetbrains.annotations.NonNls;

import java.awt.*;

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
  public static final TextAttributes SCALAR_KEY_DEFAULT_ATTRS = TextAttributesKeyDefaults
    .getDefaultAttributes(SyntaxHighlighterColors.KEYWORD).clone();
  public static final TextAttributes COMMENT_DEFAULT_ATTRS = TextAttributesKeyDefaults
    .getDefaultAttributes(SyntaxHighlighterColors.DOC_COMMENT).clone();
  public static final TextAttributes SCALAR_TEXT_DEFAULT_ATTRS = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();
  public static final TextAttributes SCALAR_STRING_DEFAULT_ATTRS = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();
  public static final TextAttributes SCALAR_DSTRING_DEFAULT_ATTRS = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();
  public static final TextAttributes SCALAR_LIST_DEFAULT_ATTRS = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();
  public static final TextAttributes TEXT_DEFAULT_ATTRS = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();
  public static final TextAttributes SIGN_DEFAULT_ATTRS = TextAttributesKeyDefaults.getDefaultAttributes(HighlighterColors.TEXT).clone();

  static {
    SCALAR_STRING_DEFAULT_ATTRS.setForegroundColor(new Color(0, 128, 128));
    SCALAR_STRING_DEFAULT_ATTRS.setFontType(Font.BOLD);

    SCALAR_DSTRING_DEFAULT_ATTRS.setForegroundColor(new Color(0, 128, 0));
    SCALAR_DSTRING_DEFAULT_ATTRS.setFontType(Font.BOLD);

    SCALAR_LIST_DEFAULT_ATTRS.setBackgroundColor(new Color(218, 233, 246));
  }

  // text attributes keys
  public static final TextAttributesKey SCALAR_KEY = TextAttributesKeyDefaults
    .createTextAttributesKey(SCALAR_KEY_ID, SCALAR_KEY_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_TEXT = TextAttributesKeyDefaults
    .createTextAttributesKey(SCALAR_TEXT_ID, SCALAR_TEXT_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_STRING =
      TextAttributesKeyDefaults.createTextAttributesKey(SCALAR_STRING_ID, SCALAR_STRING_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_DSTRING =
      TextAttributesKeyDefaults.createTextAttributesKey(SCALAR_DSTRING_ID, SCALAR_DSTRING_DEFAULT_ATTRS);
  public static final TextAttributesKey SCALAR_LIST = TextAttributesKeyDefaults
    .createTextAttributesKey(SCALAR_LIST_ID, SCALAR_LIST_DEFAULT_ATTRS);
  public static final TextAttributesKey COMMENT = TextAttributesKeyDefaults.createTextAttributesKey(COMMENT_ID, COMMENT_DEFAULT_ATTRS);
  public static final TextAttributesKey TEXT = TextAttributesKeyDefaults.createTextAttributesKey(TEXT_ID, TEXT_DEFAULT_ATTRS);
  public static final TextAttributesKey SIGN = TextAttributesKeyDefaults.createTextAttributesKey(SIGN_ID, SIGN_DEFAULT_ATTRS);

  private YAMLHighlighter() {
  }
}
