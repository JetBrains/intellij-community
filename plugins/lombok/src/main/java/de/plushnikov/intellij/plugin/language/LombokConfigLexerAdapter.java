package de.plushnikov.intellij.plugin.language;

import com.intellij.lexer.FlexAdapter;

public class LombokConfigLexerAdapter extends FlexAdapter {
  public LombokConfigLexerAdapter() {
    super(new LombokConfigLexer(null));
  }
}
