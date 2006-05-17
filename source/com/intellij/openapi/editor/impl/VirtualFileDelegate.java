package com.intellij.openapi.editor.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class VirtualFileDelegate extends LightVirtualFile {
  private final VirtualFile myDelegate;
  private final TextRange myWindow;

  public VirtualFileDelegate(@NotNull VirtualFile delegate, TextRange window, Language language, String text) {
    super(delegate.getName(), language, text);
    myDelegate = delegate;
    myWindow = window;
  }

  public VirtualFile getDelegate() {
    return myDelegate;
  }

  public TextRange getWindowRange() {
    return myWindow;
  }
}
