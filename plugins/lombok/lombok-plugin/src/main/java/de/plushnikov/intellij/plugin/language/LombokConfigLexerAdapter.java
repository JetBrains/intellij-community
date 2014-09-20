package de.plushnikov.intellij.plugin.language;

import com.intellij.lexer.FlexAdapter;

import java.io.Reader;

public class LombokConfigLexerAdapter extends FlexAdapter {
  public LombokConfigLexerAdapter() {
    super(new LombokConfigLexer((Reader) null));
  }
}
