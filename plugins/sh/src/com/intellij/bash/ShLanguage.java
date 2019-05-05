package com.intellij.bash;

import com.intellij.bash.highlighter.ShHighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;

public class ShLanguage extends Language {
  public static final Language INSTANCE = new ShLanguage();

  public ShLanguage() {
    super("Shell Script", "application/x-bsh", "application/x-sh", "text/x-script.sh");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(this, new ShHighlighterFactory());
  }
}
