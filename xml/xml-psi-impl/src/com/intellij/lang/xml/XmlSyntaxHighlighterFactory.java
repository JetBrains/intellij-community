package com.intellij.lang.xml;

import com.intellij.ide.highlighter.XmlFileHighlighter;
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

public class XmlSyntaxHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  @NotNull
  protected SyntaxHighlighter createHighlighter() {
    return new XmlFileHighlighter();
  }
}
