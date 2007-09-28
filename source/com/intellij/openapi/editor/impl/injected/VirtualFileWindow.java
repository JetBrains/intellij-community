package com.intellij.openapi.editor.impl.injected;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class VirtualFileWindow extends LightVirtualFile {
  private final VirtualFile myDelegate;
  private final DocumentWindow myDocumentWindow;

  public VirtualFileWindow(@NotNull VirtualFile delegate, @NotNull DocumentWindow window, @NotNull Language language, @NotNull CharSequence text) {
    super(delegate.getName(), language, text);
    setCharset(delegate.getCharset());
    myDelegate = delegate;
    myDocumentWindow = window;
  }

  public VirtualFile getDelegate() {
    return myDelegate;
  }

  public DocumentWindow getDocumentWindow() {
    return myDocumentWindow;
  }

  public boolean isValid() {
    return myDocumentWindow.isValid();
  }
}