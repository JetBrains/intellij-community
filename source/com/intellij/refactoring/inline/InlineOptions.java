package com.intellij.refactoring.inline;

/**
 * @author dyoma
 */
public interface InlineOptions {
  boolean isInlineThisOnly();
  void close(int exitCode);

  boolean isPreviewUsages();
}
