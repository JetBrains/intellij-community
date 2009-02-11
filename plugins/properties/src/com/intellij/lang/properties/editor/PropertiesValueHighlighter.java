package com.intellij.lang.properties.editor;

import com.intellij.lang.properties.PropertiesHighlighter;
import com.intellij.lexer.Lexer;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class PropertiesValueHighlighter extends PropertiesHighlighter {

  @NotNull
  public Lexer getHighlightingLexer() {
    return new PropertiesValueHighlightingLexer();
  }
}
