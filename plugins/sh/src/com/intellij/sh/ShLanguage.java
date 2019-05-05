package com.intellij.sh;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.sh.highlighter.ShHighlighterFactory;

public class ShLanguage extends Language {
  public static final Language INSTANCE = new ShLanguage();

  public ShLanguage() {
    super("Shell Script", "application/x-bsh", "application/x-sh", "text/x-script.sh");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(this, new ShHighlighterFactory());
  }
}
