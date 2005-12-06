
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsBundle;

import java.io.*;
import java.util.ArrayList;

/**
 *
 */
class VirtualFileDirectoryImpl extends VirtualFileImpl {
  private ArrayList<VirtualFileImpl> myChildren = new ArrayList<VirtualFileImpl>();

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
    return myChildren.toArray(new VirtualFile[myChildren.size()]);
  }

  public InputStream getInputStream() throws IOException {
    throw new IOException(VfsBundle.message("file.read.error", getUrl()));
  }

  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new IOException(VfsBundle.message("file.write.error", getUrl()));
  }

  public byte[] contentsToByteArray() throws IOException {
    throw new IOException(VfsBundle.message("file.read.error", getUrl()));
  }

  public long getModificationStamp() {
    return -1;
  }

  void addChild(VirtualFileImpl child) {
    myChildren.add(child);
  }

  void removeChild(VirtualFileImpl child) {
    myChildren.remove(child);
    child.myIsValid = false;
  }
}
