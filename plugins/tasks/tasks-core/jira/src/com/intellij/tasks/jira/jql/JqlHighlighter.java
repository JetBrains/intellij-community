package com.intellij.tasks.jira.jql;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.openapi.editor.DefaultLanguageHighlighterColors.*;

/**
 * @author Mikhail Golubev
 */
public class JqlHighlighter extends SyntaxHighlighterBase {
  private static final Map<IElementType, TextAttributesKey> KEYS = new HashMap<>();
  static {
    KEYS.put(JqlTokenTypes.STRING_LITERAL, STRING);
    KEYS.put(JqlTokenTypes.NUMBER_LITERAL, NUMBER);
    KEYS.put(JqlTokenTypes.COMMA, COMMA);
    KEYS.put(JqlTokenTypes.LPAR, PARENTHESES);
    KEYS.put(JqlTokenTypes.RPAR, PARENTHESES);
    fillMap(KEYS, JqlTokenTypes.KEYWORDS, KEYWORD);
    fillMap(KEYS, JqlTokenTypes.SIGN_OPERATORS, OPERATION_SIGN);

    KEYS.put(JqlTokenTypes.BAD_CHARACTER, HighlighterColors.BAD_CHARACTER);
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new JqlLexer();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    return pack(KEYS.get(tokenType));
  }
}
