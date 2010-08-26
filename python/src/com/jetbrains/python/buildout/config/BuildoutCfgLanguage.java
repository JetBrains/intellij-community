package com.jetbrains.python.buildout.config;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutCfgLanguage extends Language {
  public static final BuildoutCfgLanguage INSTANCE = new BuildoutCfgLanguage();

  private BuildoutCfgLanguage() {
    super("BuildoutCfg");
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExplicitExtension(this, new SingleLazyInstanceSyntaxHighlighterFactory() {
      @NotNull
      protected SyntaxHighlighter createHighlighter() {
        return new BuildoutCfgSyntaxHighlighter();
      }
    });
  }

  @Override
  public String getDisplayName() {
    return "Buildout config";
  }
}
