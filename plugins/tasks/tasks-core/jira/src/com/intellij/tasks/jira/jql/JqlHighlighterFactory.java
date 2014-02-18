package com.intellij.tasks.jira.jql;

import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JqlHighlighterFactory extends SingleLazyInstanceSyntaxHighlighterFactory {
  @NotNull
  @Override
  protected SyntaxHighlighter createHighlighter() {
    return new JqlHighlighter();
  }
}
