/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class NullVirtualFile extends VirtualFile {
  public static NullVirtualFile INSTANCE = new NullVirtualFile();

  private NullVirtualFile() {}

  @NonNls
  public String toString() {
    return "VirtualFile.NULL_OBJECT";
  }

  public byte[] contentsToByteArray() throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile[] getChildren() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    throw new UnsupportedOperationException();
  }

  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  public long getLength() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @NonNls
  public String getName() {
    throw new UnsupportedOperationException();
  }

  public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, final long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public VirtualFile getParent() {
    throw new UnsupportedOperationException();
  }

  public String getPath() {
    throw new UnsupportedOperationException();
  }

  public long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  public String getUrl() {
    throw new UnsupportedOperationException();
  }

  public boolean isDirectory() {
    throw new UnsupportedOperationException();
  }

  public boolean isValid() {
    throw new UnsupportedOperationException();
  }

  public boolean isWritable() {
    throw new UnsupportedOperationException();
  }

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    throw new UnsupportedOperationException();
  }
}