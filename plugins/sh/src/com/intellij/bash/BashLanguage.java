package com.intellij.bash;


import com.intellij.bash.highlighter.BashHighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;

public class BashLanguage extends Language {
  public static final Language INSTANCE = new BashLanguage();

  public BashLanguage() {
    super("Shell Script", "application/x-bsh", "application/x-sh", "text/x-script.sh");

    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(this, new BashHighlighterFactory());
  }
}
