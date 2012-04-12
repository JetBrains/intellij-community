package com.jetbrains.typoscript.lang.highlighter;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.SyntaxHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptHighlightingData {
  public static final TextAttributesKey ONE_LINE_COMMENT =
    TextAttributesKey.createTextAttributesKey("TS_ONE_LINE_COMMENT", SyntaxHighlighterColors.LINE_COMMENT.getDefaultAttributes());
  public static final TextAttributesKey MULTILINE_COMMENT =
    TextAttributesKey.createTextAttributesKey("TS_MULTILINE_COMMENT", SyntaxHighlighterColors.JAVA_BLOCK_COMMENT.getDefaultAttributes());
  public static final TextAttributesKey IGNORED_TEXT =
    TextAttributesKey.createTextAttributesKey("TS_IGNORED_TEXT", SyntaxHighlighterColors.LINE_COMMENT.getDefaultAttributes());
  public static final TextAttributesKey OPERATOR_SIGN =
    TextAttributesKey.createTextAttributesKey("TS_OPERATOR_SIGN", SyntaxHighlighterColors.OPERATION_SIGN.getDefaultAttributes());
  public static final TextAttributesKey STRING_VALUE =
    TextAttributesKey.createTextAttributesKey("STRING_VALUE", SyntaxHighlighterColors.STRING.getDefaultAttributes());
  public static final TextAttributesKey ASSIGNED_VALUE =
    TextAttributesKey.createTextAttributesKey("ASSIGNED_VALUE", HighlighterColors.TEXT.getDefaultAttributes());
  public static final TextAttributesKey OBJECT_PATH_ENTITY =
    TextAttributesKey.createTextAttributesKey("TS_OBJECT_PATH_ENTITY", SyntaxHighlighterColors.KEYWORD.getDefaultAttributes());
  public static final TextAttributesKey OBJECT_PATH_SEPARATOR =
    TextAttributesKey.createTextAttributesKey("TS_OBJECT_PATH_SEPARATOR", SyntaxHighlighterColors.KEYWORD.getDefaultAttributes());


  /*
IElementType MODIFICATION_OPERATOR_FUNCTION = new TypoScriptTokenType("MODIFICATION_FUNCTION");
IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN = new TypoScriptTokenType("MODIFICATION_OPEN");
IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_END = new TypoScriptTokenType("MODIFICATION_CLOSE");
IElementType MODIFICATION_OPERATOR_FUNCTION_ARGUMENT = new TypoScriptTokenType("MODIFICATION_VALUE");
  */
  public static final TextAttributesKey BAD_CHARACTER =
    TextAttributesKey.createTextAttributesKey("TS_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER.getDefaultAttributes());
}
