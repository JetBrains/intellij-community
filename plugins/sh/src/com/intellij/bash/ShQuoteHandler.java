package com.intellij.bash;

import com.intellij.bash.lexer.ShTokenTypes;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;

public class ShQuoteHandler extends SimpleTokenSetQuoteHandler {
  public ShQuoteHandler() {
    super(ShTokenTypes.BAD_CHARACTER, ShTypes.RAW_STRING, ShTypes.QUOTE, ShTypes.BACKQUOTE);
  }
}
