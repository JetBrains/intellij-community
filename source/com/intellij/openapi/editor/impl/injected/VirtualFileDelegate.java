package com.intellij.openapi.editor.impl.injected;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class VirtualFileDelegate extends LightVirtualFile {
  private final VirtualFile myDelegate;
  private final DocumentRange myDocumentRange;

  public VirtualFileDelegate(@NotNull VirtualFile delegate, @NotNull DocumentRange window, @NotNull Language language, @NotNull String text) {
    super(delegate.getName(), language, text);
    setCharset(delegate.getCharset());
    myDelegate = delegate;
    myDocumentRange = window;
  }

  public VirtualFile getDelegate() {
    return myDelegate;
  }

  public DocumentRange getDocumentRange() {
    return myDocumentRange;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    VirtualFileDelegate that = (VirtualFileDelegate)o;

    if (myDelegate != that.myDelegate) return false;
    if (!getContent().equals(that.getContent())) return false;
    return myDocumentRange.equalsTo(that.myDocumentRange);
  }

  public int hashCode() {
    return getContent().hashCode();
  }
}
