package com.intellij.tokenindex;

import com.intellij.lang.LanguageExtension;

/**
 * @author Eugene.Kudelevsky
 */
public class LanguageTokenizer extends LanguageExtension<Tokenizer> {
  public static final LanguageTokenizer INSTANCE = new LanguageTokenizer();

  private LanguageTokenizer() {
    super("com.intellij.tokenindex.tokenizer", null);
  }
}
