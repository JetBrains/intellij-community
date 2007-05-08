package com.intellij.openapi.vfs.ex.http;

import com.intellij.openapi.vfs.DeprecatedVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class VirtualFileImpl extends DeprecatedVirtualFile {

  private final HttpFileSystem myFileSystem;

  private String myPath;
  private String myParentPath;
  private String myName;

  VirtualFileImpl(HttpFileSystem fileSystem, String path) {
    myFileSystem = fileSystem;
    myPath = path;
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash == path.length() - 1) {
      myParentPath = null;
      myName = path;
    }
    else {
      int prevSlash = path.lastIndexOf('/', lastSlash - 1);
      if (prevSlash < 0) {
        myParentPath = path.substring(0, lastSlash + 1);
        myName = path.substring(lastSlash + 1);
      }
      else {
        myParentPath = path.substring(0, lastSlash);
        myName = path.substring(lastSlash + 1);
      }
    }
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    return myPath;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public VirtualFile getParent() {
    if (myParentPath == null) return null;
    return myFileSystem.findFileByPath(myParentPath);
  }

  public boolean isWritable() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isDirectory() {
    return true;
  }

  public VirtualFile[] getChildren() {
    throw new UnsupportedOperationException();
  }

  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  public byte[] contentsToByteArray() throws IOException {
    throw new UnsupportedOperationException();
  }

  public long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  public long getLength() {
    return -1;
  }

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    throw new UnsupportedOperationException();
  }

  ;
}