
package com.intellij.openapi.vfs.ex.dummy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;

import java.io.IOException;

/**
 *
 */
abstract class VirtualFileImpl extends VirtualFile {
  protected final DummyFileSystem myFileSystem;
  protected VirtualFileDirectoryImpl myParent;
  private boolean myRootFlag;
  private String myName;
  private boolean myIsValid = true;

  protected VirtualFileImpl(DummyFileSystem fileSystem, VirtualFileDirectoryImpl parent, String name) {
    myFileSystem = fileSystem;
    myParent = parent;
    myRootFlag = parent != null;
    myName = name;
  }

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

  public String getName() {
    return myName;
  }

  public void rename(Object requestor, String newName) throws IOException {
    if (myName.equals(newName)){
      return;
    }
    myName = newName;
  }

  public boolean isWritable() {
    return true;
  }

  public boolean isValid() {
    return myIsValid;
/*
    if (myParent != null){
      return myParent.isValid();
    }
    else{
      return myRootFlag;
    }
*/
  }

  public VirtualFile getParent() {
    return myParent;
  }

  public void delete(Object requestor) throws IOException {
    if (myParent == null){
      throw new IOException("Cannot delete root file " + getPresentableUrl() + ".");
    }
    myParent.removeChild(this);
    myIsValid = false;
  }

  public void move(Object requestor, VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException();
  }

  public long getTimeStamp() {
    return -1;
  }

  public long getActualTimeStamp() {
    return -1;
  }

  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
  }
}
