
package com.intellij.openapi.vfs.ex.http;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.*;

class VirtualFileImpl extends VirtualFile {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.ex.http.VirtualFileImpl");

  private final HttpFileSystem myFileSystem;

  private String myPath;
  private String myParentPath;
  private String myName;

  private static final VirtualFileImpl[] EMPTY_VIRTUAL_FILE_ARRAY = new VirtualFileImpl[0];

  VirtualFileImpl(HttpFileSystem fileSystem, String path) {
    myFileSystem = fileSystem;
    myPath = path;
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash == path.length() - 1){
      myParentPath = null;
      myName = path;
    }
    else{
      int prevSlash = path.lastIndexOf('/', lastSlash - 1);
      if (prevSlash < 0){
        myParentPath = path.substring(0, lastSlash + 1);
        myName = path.substring(lastSlash + 1);
      }
      else{
        myParentPath = path.substring(0, lastSlash);
        myName = path.substring(lastSlash + 1);
      }
    }
  }

  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    return myPath;
  }

  public String getName() {
    return myName;
  }

  public VirtualFile getParent() {
    if (myParentPath == null) return null;
    return myFileSystem.findFileByPath(myParentPath);
  }

  public void rename(Object requestor, String newName) throws IOException {
    throw new UnsupportedOperationException();
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

  public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
    throw new UnsupportedOperationException();
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void delete(Object requestor) throws IOException {
    throw new UnsupportedOperationException();
  }

  public void move(Object requestor, VirtualFile newParent) throws IOException {
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

  public char[] contentsToCharArray() throws IOException {
    throw new UnsupportedOperationException();
  }

  public long getModificationStamp() {
    throw new UnsupportedOperationException();
  }

  public long getTimeStamp() {
    throw new UnsupportedOperationException();
  }

  public long getActualTimeStamp() {
    throw new UnsupportedOperationException();
  }

  public long getLength() {
    return -1;
  }

  public void refresh(final boolean asynchronous, final boolean recursive, final Runnable postRunnable) {
    throw new UnsupportedOperationException();
  };

  public String toString() {
    return getPath();
  }
}