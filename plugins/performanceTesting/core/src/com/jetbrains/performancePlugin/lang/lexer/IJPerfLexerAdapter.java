package com.jetbrains.performancePlugin.lang.lexer;

import com.intellij.lexer.FlexAdapter;

public class IJPerfLexerAdapter extends FlexAdapter {

  public IJPerfLexerAdapter() {
    super(new IJPerfLexer(null));
  }
}
