package de.plushnikov.intellij.plugin.language;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import de.plushnikov.intellij.plugin.language.psi.LombokConfigTypes;
import org.jetbrains.annotations.NotNull;

public class LombokConfigSyntaxHighlighter extends SyntaxHighlighterBase {
  private static final TextAttributesKey CLEAR = TextAttributesKey.createTextAttributesKey("LOMBOK_CLEAN", DefaultLanguageHighlighterColors.CONSTANT);
  public static final TextAttributesKey SEPARATOR = TextAttributesKey.createTextAttributesKey("LOMBOK_SEPARATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
  public static final TextAttributesKey KEY = TextAttributesKey.createTextAttributesKey("LOMBOK_KEY", DefaultLanguageHighlighterColors.KEYWORD);
  public static final TextAttributesKey VALUE = TextAttributesKey.createTextAttributesKey("LOMBOK_VALUE", DefaultLanguageHighlighterColors.STRING);
  private static final TextAttributesKey COMMENT = TextAttributesKey.createTextAttributesKey("LOMBOK_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);

  private static final TextAttributesKey BAD_CHARACTER = TextAttributesKey.createTextAttributesKey("LOMBOK_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);

  private static final TextAttributesKey[] BAD_CHAR_KEYS = new TextAttributesKey[]{BAD_CHARACTER};
  private static final TextAttributesKey[] SEPARATOR_KEYS = new TextAttributesKey[]{SEPARATOR};
  private static final TextAttributesKey[] KEY_KEYS = new TextAttributesKey[]{KEY};
  private static final TextAttributesKey[] VALUE_KEYS = new TextAttributesKey[]{VALUE};
  private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
  private static final TextAttributesKey[] CLEAR_KEYS = new TextAttributesKey[]{CLEAR};
  private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new LombokConfigLexerAdapter();
  }

  @NotNull
  @Override
  public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
    if (tokenType.equals(LombokConfigTypes.SEPARATOR) || tokenType.equals(LombokConfigTypes.SIGN)) {
      return SEPARATOR_KEYS;
    } else if (tokenType.equals(LombokConfigTypes.CLEAR)) {
      return CLEAR_KEYS;
    } else if (tokenType.equals(LombokConfigTypes.KEY)) {
      return KEY_KEYS;
    } else if (tokenType.equals(LombokConfigTypes.VALUE)) {
      return VALUE_KEYS;
    } else if (tokenType.equals(LombokConfigTypes.COMMENT)) {
      return COMMENT_KEYS;
    } else if (tokenType.equals(TokenType.BAD_CHARACTER)) {
      return BAD_CHAR_KEYS;
    } else {
      return EMPTY_KEYS;
    }
  }
}
