package com.intellij.bash;

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

class BashHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  @NotNull
  protected SyntaxHighlighter createHighlighter() {
    return new BashSyntaxHighlighter();
  }
}
