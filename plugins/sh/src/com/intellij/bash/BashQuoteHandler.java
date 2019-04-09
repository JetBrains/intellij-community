package com.intellij.bash;

import com.intellij.bash.lexer.BashTokenTypes;
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;

public class BashQuoteHandler extends SimpleTokenSetQuoteHandler {
  public BashQuoteHandler() {
    super(BashTokenTypes.BAD_CHARACTER, BashTypes.RAW_STRING, BashTypes.QUOTE, BashTypes.BACKQUOTE);
  }
}
