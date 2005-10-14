package com.intellij.refactoring;

import com.intellij.refactoring.inline.InlineOptions;

/**
 * @author dyoma
 */
public class MockInlineMethodOptions implements InlineOptions {
  public boolean isInlineThisOnly() {
    return false;
  }

  public void close(int exitCode) {
  }

  public boolean isPreviewUsages() {
    return false;
  }
}
