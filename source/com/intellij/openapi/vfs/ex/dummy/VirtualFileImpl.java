
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NotNull;

/**
 *
 */
abstract class VirtualFileImpl extends VirtualFile {
  protected final DummyFileSystem myFileSystem;
  protected VirtualFileDirectoryImpl myParent;
  private String myName;
  protected boolean myIsValid = true;

  protected VirtualFileImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    myFileSystem = fileSystem;
    myParent = parent;
    myName = name;
  }

  @NotNull
  public VirtualFileSystem getFileSystem() {
    return myFileSystem;
  }

  public String getPath() {
    if (myParent == null) {
      return myName;
    } else {
      return myParent.getPath() + "/" + myName;
    }
  }

  @NotNull
  public String getName() {
    return myName;
  }

  void setName(final String name) {
    myName = name;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isValid() {
    return myIsValid;
  }

  public VirtualFile getParent() {
    return myParent;
  }

  public long getTimeStamp() {
    return -1;
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }
}
