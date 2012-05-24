package com.jetbrains.typoscript.lang.highlighter;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.typoscript.lang.TypoScriptLexer;
import com.jetbrains.typoscript.lang.TypoScriptTokenTypes;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author lene
 *         Date: 11.04.12
 */
public class TypoScriptSyntaxHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> ourMap;

  static {
    ourMap = new HashMap<IElementType, TextAttributesKey>();
    ourMap.put(TypoScriptTokenTypes.ONE_LINE_COMMENT, TypoScriptHighlightingData.ONE_LINE_COMMENT);
    ourMap.put(TypoScriptTokenTypes.IGNORED_TEXT, TypoScriptHighlightingData.IGNORED_TEXT);
    ourMap.put(TypoScriptTokenTypes.C_STYLE_COMMENT, TypoScriptHighlightingData.MULTILINE_COMMENT);

    fillMap(ourMap, TypoScriptTokenTypes.OPERATORS, TypoScriptHighlightingData.OPERATOR_SIGN);

    ourMap.put(TypoScriptTokenTypes.MULTILINE_VALUE, TypoScriptHighlightingData.ASSIGNED_VALUE);
    ourMap.put(TypoScriptTokenTypes.ASSIGNMENT_VALUE, TypoScriptHighlightingData.ASSIGNED_VALUE);

    ourMap.put(TypoScriptTokenTypes.OBJECT_PATH_ENTITY, TypoScriptHighlightingData.OBJECT_PATH_ENTITY);
    ourMap.put(TypoScriptTokenTypes.OBJECT_PATH_SEPARATOR, TypoScriptHighlightingData.OBJECT_PATH_SEPARATOR);

    ourMap.put(TypoScriptTokenTypes.CONDITION, TypoScriptHighlightingData.CONDITION);
    ourMap.put(TypoScriptTokenTypes.INCLUDE_STATEMENT, TypoScriptHighlightingData.INCLUDE_STATEMENT);

    ourMap.put(TypoScriptTokenTypes.BAD_CHARACTER, TypoScriptHighlightingData.BAD_CHARACTER);

    /*
 IElementType MODIFICATION_OPERATOR_FUNCTION = new TypoScriptTokenType("MODIFICATION_FUNCTION");
 IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_BEGIN = new TypoScriptTokenType("MODIFICATION_OPEN");
 IElementType MODIFICATION_OPERATOR_FUNCTION_PARAM_END = new TypoScriptTokenType("MODIFICATION_CLOSE");
 IElementType MODIFICATION_OPERATOR_FUNCTION_ARGUMENT = new TypoScriptTokenType("MODIFICATION_VALUE");

 IElementType INCLUDE_STATEMENT = new TypoScriptTokenType("INCLUDE");
 IElementType CONDITION = new TypoScriptTokenType("CONDITION");
    */
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new TypoScriptLexer();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(ourMap.get(tokenType));
  }
}
