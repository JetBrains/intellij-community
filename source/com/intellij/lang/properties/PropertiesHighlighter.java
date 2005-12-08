package com.intellij.lang.properties;

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 */
public class PropertiesHighlighter extends SyntaxHighlighterBase {
  private static Map<IElementType, TextAttributesKey> keys1;
  private static Map<IElementType, TextAttributesKey> keys2;

  @NotNull
  public Lexer getHighlightingLexer() {
    return new PropertiesHighlightingLexer();
  }

  public static final TextAttributesKey PROPERTY_KEY = TextAttributesKey.createTextAttributesKey(
                                                "PROPERTIES.KEY",
                                                HighlighterColors.JAVA_KEYWORD.getDefaultAttributes()
                                              );

  public static final TextAttributesKey PROPERTY_VALUE = TextAttributesKey.createTextAttributesKey(
                                               "PROPERTIES.VALUE",
                                               HighlighterColors.JAVA_STRING.getDefaultAttributes()
                                             );

  public static final TextAttributesKey PROPERTY_COMMENT = TextAttributesKey.createTextAttributesKey(
                                                     "PROPERTIES.LINE_COMMENT",
                                                     HighlighterColors.JAVA_LINE_COMMENT.getDefaultAttributes()
                                                   );

  public static final TextAttributesKey PROPERTY_KEY_VALUE_SEPARATOR = TextAttributesKey.createTextAttributesKey(
                                                       "PROPERTIES.KEY_VALUE_SEPARATOR",
                                                       HighlighterColors.JAVA_OPERATION_SIGN.getDefaultAttributes()
                                                     );
  public static final TextAttributesKey PROPERTIES_VALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
                                                       "PROPERTIES.VALID_STRING_ESCAPE",
                                                       HighlighterColors.JAVA_VALID_STRING_ESCAPE.getDefaultAttributes()
                                                     );
  public static final TextAttributesKey PROPERTIES_INVALID_STRING_ESCAPE = TextAttributesKey.createTextAttributesKey(
                                                       "PROPERTIES.INVALID_STRING_ESCAPE",
                                                       HighlighterColors.JAVA_INVALID_STRING_ESCAPE.getDefaultAttributes()
                                                     );

  static {
    keys1 = new THashMap<IElementType, TextAttributesKey>();
    keys2 = new THashMap<IElementType, TextAttributesKey>();

    keys1.put(PropertiesTokenTypes.VALUE_CHARACTERS, PROPERTY_VALUE);
    keys1.put(PropertiesTokenTypes.END_OF_LINE_COMMENT, PROPERTY_COMMENT);
    keys1.put(PropertiesTokenTypes.KEY_CHARACTERS, PROPERTY_KEY);
    keys1.put(PropertiesTokenTypes.KEY_VALUE_SEPARATOR, PROPERTY_KEY_VALUE_SEPARATOR);

    keys1.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, PROPERTIES_VALID_STRING_ESCAPE);
    // in fact all back-slashed escapes are allowed
    keys1.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, PROPERTIES_INVALID_STRING_ESCAPE);
    keys1.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, PROPERTIES_INVALID_STRING_ESCAPE);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(keys1.get(tokenType), keys2.get(tokenType));
  }

  public static Map<IElementType, TextAttributesKey> getKeys1() {
    return (Map<IElementType, TextAttributesKey>)((HashMap)keys1).clone();
  }

  public static Map<IElementType, TextAttributesKey> getKeys2() {
    return (Map<IElementType, TextAttributesKey>)((HashMap)keys2).clone();
  }
}
