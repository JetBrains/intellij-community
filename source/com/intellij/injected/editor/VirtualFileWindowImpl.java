package com.intellij.injected.editor;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class VirtualFileWindowImpl extends LightVirtualFile implements VirtualFileWindow {
  private final VirtualFile myDelegate;
  private final DocumentWindowImpl myDocumentWindow;

  public VirtualFileWindowImpl(@NotNull VirtualFile delegate, @NotNull DocumentWindowImpl window, @NotNull Language language, @NotNull CharSequence text) {
    super(delegate.getName(), language, text);
    setCharset(delegate.getCharset());
    myDelegate = delegate;
    myDocumentWindow = window;
  }

  public VirtualFile getDelegate() {
    return myDelegate;
  }

  public DocumentWindowImpl getDocumentWindow() {
    return myDocumentWindow;
  }

  public boolean isValid() {
    return myDocumentWindow.isValid();
  }

  public boolean isWritable() {
    return getDelegate().isWritable();
  }
}