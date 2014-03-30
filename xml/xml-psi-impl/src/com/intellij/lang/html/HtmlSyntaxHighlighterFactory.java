package com.intellij.lang.html;

import com.intellij.ide.highlighter.HtmlFileHighlighter;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class HtmlSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  @NotNull
  protected SyntaxHighlighter createHighlighter() {
    return new HtmlFileHighlighter();
  }
}
