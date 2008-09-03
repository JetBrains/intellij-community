/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lexer.EmptyLexer;
import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.StringLiteralLexer;
import com.intellij.psi.tree.IElementType;

public class PropertiesValueHighlightingLexer extends LayeredLexer {
  public PropertiesValueHighlightingLexer() {
      super(new EmptyLexer(){
        public IElementType getTokenType() {
          return getTokenStart() < getTokenEnd() ? PropertiesTokenTypes.VALUE_CHARACTERS : null;
        }
      });
      registerSelfStoppingLayer(new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, PropertiesTokenTypes.VALUE_CHARACTERS, true, "#!=:"),
                                new IElementType[]{PropertiesTokenTypes.VALUE_CHARACTERS}, IElementType.EMPTY_ARRAY);
  }
}