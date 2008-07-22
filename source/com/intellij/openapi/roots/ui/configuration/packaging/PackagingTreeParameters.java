package com.intellij.openapi.roots.ui.configuration.packaging;

/**
 * @author nik
 */
public class PackagingTreeParameters {
  private boolean myShowIncludedContent;

  public PackagingTreeParameters(final boolean showIncludedContent) {
    myShowIncludedContent = showIncludedContent;
  }

  public boolean isShowIncludedContent() {
    return myShowIncludedContent;
  }
}
