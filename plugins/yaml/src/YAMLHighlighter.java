// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NonNls;

public final class YAMLHighlighter {
  static final @NonNls String SCALAR_KEY_ID = "YAML_SCALAR_KEY";
  static final @NonNls String SCALAR_TEXT_ID = "YAML_SCALAR_VALUE";
  static final @NonNls String SCALAR_STRING_ID = "YAML_SCALAR_STRING";
  static final @NonNls String SCALAR_DSTRING_ID = "YAML_SCALAR_DSTRING";
  static final @NonNls String SCALAR_LIST_ID = "YAML_SCALAR_LIST";
  static final @NonNls String COMMENT_ID = "YAML_COMMENT";
  static final @NonNls String TEXT_ID = "YAML_TEXT";
  static final @NonNls String SIGN_ID = "YAML_SIGN";
  static final @NonNls String ANCHOR_ID = "YAML_ANCHOR";

  // text attributes keys
  public static final TextAttributesKey SCALAR_KEY = TextAttributesKey
    .createTextAttributesKey(SCALAR_KEY_ID, DefaultLanguageHighlighterColors.KEYWORD);

  public static final TextAttributesKey SCALAR_TEXT = TextAttributesKey
    .createTextAttributesKey(SCALAR_TEXT_ID, HighlighterColors.TEXT);

  public static final TextAttributesKey SCALAR_STRING = TextAttributesKey
    .createTextAttributesKey(SCALAR_STRING_ID, DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey SCALAR_DSTRING = TextAttributesKey
    .createTextAttributesKey(SCALAR_DSTRING_ID, DefaultLanguageHighlighterColors.STRING);

  public static final TextAttributesKey SCALAR_LIST = TextAttributesKey
    .createTextAttributesKey(SCALAR_LIST_ID, HighlighterColors.TEXT);

  public static final TextAttributesKey COMMENT = TextAttributesKey
    .createTextAttributesKey(COMMENT_ID, DefaultLanguageHighlighterColors.DOC_COMMENT);

  public static final TextAttributesKey TEXT = TextAttributesKey
    .createTextAttributesKey(TEXT_ID, HighlighterColors.TEXT);

  public static final TextAttributesKey SIGN = TextAttributesKey
    .createTextAttributesKey(SIGN_ID, DefaultLanguageHighlighterColors.OPERATION_SIGN);

  public static final TextAttributesKey ANCHOR = TextAttributesKey
    .createTextAttributesKey(ANCHOR_ID, DefaultLanguageHighlighterColors.IDENTIFIER);

  private YAMLHighlighter() {
  }
}
