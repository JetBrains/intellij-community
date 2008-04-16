package com.intellij.lang.properties;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PropertiesLanguage extends Language {
  public static final PropertiesLanguage INSTANCE = new PropertiesLanguage();

  public PropertiesLanguage() {
    super("Properties", "text/properties");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new PropertiesHighlighter();
      }
    });
  }
}
