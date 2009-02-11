package com.intellij.lang.properties;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.properties.parsing.PropertiesLexer;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;

/**
 * @author cdr
 */
public class PropertiesHighlightingLexer extends LayeredLexer{
  public PropertiesHighlightingLexer() {
    super(new PropertiesLexer());
    registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, PropertiesTokenTypes.VALUE_CHARACTERS, true, "#!=:"),
                              new IElementType[]{PropertiesTokenTypes.VALUE_CHARACTERS},
                              IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, PropertiesTokenTypes.KEY_CHARACTERS, true, "#!=: "),
                              new IElementType[]{PropertiesTokenTypes.KEY_CHARACTERS},
                              IElementType.EMPTY_ARRAY);
  }
}
