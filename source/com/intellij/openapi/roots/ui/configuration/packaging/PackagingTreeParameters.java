package com.intellij.openapi.roots.ui.configuration.packaging;

/**
 * @author nik
 */
public class PackagingTreeParameters {
  private boolean myShowIncludedContent;
  private boolean myShowLibraryFiles;

  public PackagingTreeParameters(final boolean showIncludedContent, final boolean showLibraryFiles) {
    myShowIncludedContent = showIncludedContent;
    myShowLibraryFiles = showLibraryFiles;
  }

  public boolean isShowIncludedContent() {
    return myShowIncludedContent;
  }

  public boolean isShowLibraryFiles() {
    return myShowLibraryFiles;
  }
}
