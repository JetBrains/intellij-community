
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;

import java.io.*;
import java.util.ArrayList;

/**
 *
 */
class VirtualFileDirectoryImpl extends VirtualFileImpl {
  private ArrayList myChildren = new ArrayList();

  public VirtualFileDirectoryImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    super(fileSystem, parent, name);
  }

  public boolean isDirectory() {
    return true;
  }

  public long getLength() {
    return 0;
  }

  public VirtualFile[] getChildren() {
    return (VirtualFile[])myChildren.toArray(new VirtualFile[myChildren.size()]);
  }

  public VirtualFile createChildDirectory(Object requestor, String name) throws IOException {
    VirtualFile file = findChild(name);
    if (file != null){
      throw new IOException("Cannot create file " + getUrl() + "/" + name + ". File already exists.");
    }
    VirtualFileImpl child = new VirtualFileDirectoryImpl(myFileSystem, this, name);
    addChild(child);
    return child;
  }

  public VirtualFile createChildData(Object requestor, String name) throws IOException {
    VirtualFile file = findChild(name);
    if (file != null){
      throw new IOException("Cannot create file " + getUrl() + "/" + name + ". File already exists.");
    }
    VirtualFileImpl child = new VirtualFileDataImpl(myFileSystem, this, name);
    addChild(child);
    return child;
  }

  public InputStream getInputStream() throws IOException {
    throw new IOException("Cannot read from file " + getUrl() + ".");
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException("Cannot write to file " + getUrl() + ".");
  }

  public byte[] contentsToByteArray() throws IOException {
    throw new IOException("Cannot read from file " + getUrl() + ".");
  }

  public char[] contentsToCharArray() throws IOException {
    throw new IOException("Cannot read from file " + getUrl() + ".");
  }

  public long getModificationStamp() {
    return -1;
  }

  void addChild(VirtualFileImpl child) {
    myChildren.add(child);
    myFileSystem.fireFileCreated(null, child);
  }

  void removeChild(VirtualFileImpl child) {
    myFileSystem.fireBeforeFileDeletion(null, child);
    myChildren.remove(child);
    myFileSystem.fireFileDeleted(null, child, child.getName(), child.isDirectory(), this);
  }
}
