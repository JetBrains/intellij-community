package com.intellij.bash.highlighter;

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class ShHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  @NotNull
  protected SyntaxHighlighter createHighlighter() {
    return new ShSyntaxHighlighter();
  }
}
