package com.intellij.openapi.editor.impl;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class VirtualFileDelegate extends LightVirtualFile {
  private final VirtualFile myDelegate;
  private final DocumentRange myDocumentRange;

  public VirtualFileDelegate(@NotNull VirtualFile delegate, DocumentRange window, Language language, String text) {
    super(delegate.getName(), language, text);
    myDelegate = delegate;
    myDocumentRange = window;
  }

  public VirtualFile getDelegate() {
    return myDelegate;
  }

  public RangeMarker getTextRange() {
    return myDocumentRange.getTextRange();
  }

  public DocumentRange getDocumentRange() {
    return myDocumentRange;
  }
}
