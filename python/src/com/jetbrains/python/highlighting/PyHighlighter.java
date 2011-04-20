package com.jetbrains.python.highlighting;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.StringEscapesTokenTypes;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.lexer.PyStringLiteralLexer;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.editor.SyntaxHighlighterColors.*;

/**
 * Colors and lexer(s) needed for highlighting.
 */
public class PyHighlighter extends SyntaxHighlighterBase {
  private Map<IElementType, TextAttributesKey> keys;
  private final LanguageLevel myLanguageLevel;

  @NotNull
  public Lexer getHighlightingLexer() {
    LayeredLexer ret = new LayeredLexer(new PythonHighlightingLexer(myLanguageLevel));
    ret.registerSelfStoppingLayer(
      new PyStringLiteralLexer(PyTokenTypes.STRING_LITERAL, myLanguageLevel.isPy3K()),
      new IElementType[]{PyTokenTypes.STRING_LITERAL}, IElementType.EMPTY_ARRAY
    );
    return ret;
  }

  private static TextAttributesKey _copy(String name, TextAttributesKey src) {
    return TextAttributesKey.createTextAttributesKey(name, src.getDefaultAttributes().clone());
  }

  static final TextAttributesKey PY_KEYWORD = _copy("PY.KEYWORD", KEYWORD);

  public static final TextAttributesKey PY_STRING = _copy("PY.STRING", STRING);
  public static final TextAttributesKey PY_NUMBER = _copy("PY.NUMBER", NUMBER);

  static final TextAttributesKey PY_LINE_COMMENT = _copy("PY.LINE_COMMENT", LINE_COMMENT);

  static final TextAttributesKey PY_OPERATION_SIGN = _copy("PY.OPERATION_SIGN", OPERATION_SIGN);

  static final TextAttributesKey PY_PARENTHS = _copy("PY.PARENTHS", PARENTHS);

  static final TextAttributesKey PY_BRACKETS = _copy("PY.BRACKETS", BRACKETS);

  static final TextAttributesKey PY_BRACES = _copy("PY.BRACES", BRACES);

  static final TextAttributesKey PY_COMMA = _copy("PY.COMMA", COMMA);

  static final TextAttributesKey PY_DOT = _copy("PY.DOT", DOT);

  public static final TextAttributesKey PY_DOC_COMMENT = _copy("PY.DOC_COMMENT", DOC_COMMENT);

  public static final TextAttributesKey PY_DOC_COMMENT_TAG = _copy("PY.DOC_COMMENT_TAG", DOC_COMMENT_TAG);

  public static final TextAttributesKey PY_DECORATOR = TextAttributesKey.createTextAttributesKey(
    "PY.DECORATOR", new TextAttributes(Color.blue.darker(), null, null, null, Font.PLAIN)
  );

  public static final TextAttributesKey PY_CLASS_DEFINITION = TextAttributesKey.createTextAttributesKey(
    "PY.CLASS_DEFINITION", new TextAttributes(Color.black, null, null, null, Font.BOLD)
  );

  public static final TextAttributesKey PY_FUNC_DEFINITION = TextAttributesKey.createTextAttributesKey(
    "PY.FUNC_DEFINITION", new TextAttributes(Color.black, null, null, null, Font.BOLD)
  );

  public static final TextAttributesKey PY_PREDEFINED_DEFINITION = TextAttributesKey.createTextAttributesKey(
    "PY.PREDEFINED_DEFINITION", new TextAttributes(Color.magenta.darker(), null, null, null, Font.BOLD)
  );

  public static final TextAttributesKey PY_PREDEFINED_USAGE = TextAttributesKey.createTextAttributesKey(
    "PY.PREDEFINED_USAGE", new TextAttributes(Color.magenta.darker(), null, null, null, Font.PLAIN)
  );

  public static final TextAttributesKey PY_BUILTIN_NAME = TextAttributesKey.createTextAttributesKey(
    "PY.BUILTIN_NAME", new TextAttributes(KEYWORD.getDefaultAttributes().getForegroundColor(), null, null, null, Font.PLAIN)
  );

  public static final TextAttributesKey PY_VALID_STRING_ESCAPE = _copy("PY.VALID_STRING_ESCAPE", VALID_STRING_ESCAPE);

  public static final TextAttributesKey PY_INVALID_STRING_ESCAPE = _copy("PY.INVALID_STRING_ESCAPE", INVALID_STRING_ESCAPE);


  public PyHighlighter(LanguageLevel languageLevel) {
    myLanguageLevel = languageLevel;
    keys = new HashMap<IElementType, TextAttributesKey>();

    fillMap(keys, PyTokenTypes.KEYWORDS, PY_KEYWORD);
    fillMap(keys, PyTokenTypes.OPERATIONS, PY_OPERATION_SIGN);

    keys.put(PyTokenTypes.INTEGER_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.FLOAT_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.IMAGINARY_LITERAL, PY_NUMBER);
    keys.put(PyTokenTypes.STRING_LITERAL, PY_STRING);

    keys.put(PyTokenTypes.LPAR, PY_PARENTHS);
    keys.put(PyTokenTypes.RPAR, PY_PARENTHS);

    keys.put(PyTokenTypes.LBRACE, PY_BRACES);
    keys.put(PyTokenTypes.RBRACE, PY_BRACES);

    keys.put(PyTokenTypes.LBRACKET, PY_BRACKETS);
    keys.put(PyTokenTypes.RBRACKET, PY_BRACKETS);

    keys.put(PyTokenTypes.COMMA, PY_COMMA);
    keys.put(PyTokenTypes.DOT, PY_DOT);

    keys.put(PyTokenTypes.END_OF_LINE_COMMENT, PY_LINE_COMMENT);
    keys.put(PyTokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);

    keys.put(StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN, PY_VALID_STRING_ESCAPE);
    keys.put(StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN, PY_INVALID_STRING_ESCAPE);
    keys.put(StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN, PY_INVALID_STRING_ESCAPE);
  }

  @NotNull
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(keys.get(tokenType));
  }
}
