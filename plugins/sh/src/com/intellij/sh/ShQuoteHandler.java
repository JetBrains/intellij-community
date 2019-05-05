package com.intellij.sh;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;
import com.intellij.sh.lexer.ShTokenTypes;

public class ShQuoteHandler extends SimpleTokenSetQuoteHandler {
  public ShQuoteHandler() {
    super(ShTokenTypes.BAD_CHARACTER, ShTypes.RAW_STRING, ShTypes.QUOTE, ShTypes.BACKQUOTE);
  }
}
