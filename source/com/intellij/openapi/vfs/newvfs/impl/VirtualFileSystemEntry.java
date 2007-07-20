/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.Charset;

public abstract class VirtualFileSystemEntry extends NewVirtualFile {
  protected static final PersistentFS ourPersistence = (PersistentFS)ManagingFS.getInstance();

  private volatile String myName;
  private volatile VirtualDirectoryImpl myParent;
  private volatile boolean myDirtyFlag = false;
  private volatile int myId;

  public VirtualFileSystemEntry(final String name, final VirtualDirectoryImpl parent, int id) {
    myName = name;
    myParent = parent;
    myId = id;
  }

  @NotNull
  public String getName() {
    // TODO: HACK!!! Get to simpler solution.
    if (myParent == null && getFileSystem() instanceof JarFileSystem) {
      String jarName = myName.substring(0, myName.length() - JarFileSystem.JAR_SEPARATOR.length());
      return jarName.substring(jarName.lastIndexOf('/') + 1);
    }

    return myName;
  }

  public VirtualFileSystemEntry getParent() {
    return myParent;
  }

  protected void appendPathOnFileSystem(StringBuilder builder, boolean includeFSBaseUrl) {
    if (myParent != null) {
      myParent.appendPathOnFileSystem(builder, includeFSBaseUrl);
    }
    else {
      if (includeFSBaseUrl) {
        builder.append(getFileSystem().getProtocol()).append("://");
      }
    }

    if (myName.length() > 0) {
      final int len = builder.length();
      if (len > 0 && builder.charAt(len - 1) != '/') {
        builder.append('/');
      }
      builder.append(myName);
    }
  }

  public boolean isDirty() {
    return myDirtyFlag;
  }

  public void markClean() {
    myDirtyFlag = false;
  }

  public void markDirty() {
    if (!myDirtyFlag) {
      myDirtyFlag = true;
      if (myParent != null) myParent.markDirty();
    }
  }

  public void markDirtyRecursively() {
    markDirty();
    for (VirtualFile file : getCachedChildren()) {
      ((VirtualFileSystemEntry)file).markDirtyRecursively();
    }
  }

  @NotNull
  public String getUrl() {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      appendPathOnFileSystem(builder, true);
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  public String getPath() {
    StringBuilder builder = new StringBuilder();
    appendPathOnFileSystem(builder, false);
    return builder.toString();
  }

  public void delete(final Object requestor) throws IOException {
    ourPersistence.deleteFile(requestor, this);
  }

  public void rename(final Object requestor, @NotNull @NonNls final String newName) throws IOException {
    if (getName().equals(newName)) return;
    if (!VfsUtil.isValidName(newName)) {
      throw new IOException(VfsBundle.message("file.invalid.name.error", newName));
    }

    ourPersistence.renameFile(requestor, this, newName);
  }

  @NotNull
  public VirtualFile createChildData(final Object requestor, @NotNull final String name) throws IOException {
    return ourPersistence.createChildFile(requestor, this, name);
  }

  public boolean isWritable() {
    return ourPersistence.isWritable(this);
  }

  public void setWritable(boolean writable) throws IOException {
    ourPersistence.setWritable(this, writable);
  }

  public long getTimeStamp() {
    return ourPersistence.getTimeStamp(this);
  }

  public void setTimeStamp(final long time) throws IOException {
    ourPersistence.setTimeStamp(this, time);
  }

  public long getLength() {
    return ourPersistence.getLength(this);
  }

  public VirtualFile copy(final Object requestor, final VirtualFile newParent, final String copyName) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.copy.error", newParent.getPresentableUrl()));
    }

    if (!newParent.isDirectory()) {
      throw new IOException(VfsBundle.message("file.copy.target.must.be.directory"));
    }

    return ourPersistence.copyFile(requestor, this, newParent, copyName);
  }

  public void move(final Object requestor, final VirtualFile newParent) throws IOException {
    if (getFileSystem() != newParent.getFileSystem()) {
      throw new IOException(VfsBundle.message("file.move.error", newParent.getPresentableUrl()));
    }

    ourPersistence.moveFile(requestor, this, newParent);
  }

  public int getId() {
    return myId;
  }

  @NotNull
  public VirtualFile createChildDirectory(final Object requestor, final String name) throws IOException {
    return ourPersistence.createChildDirectory(requestor, this, name);
  }

  public boolean exists() {
    return ourPersistence.exists(this);
  }


  public boolean isValid() {
    return exists();
  }


  public String toString() {
    return getUrl();
  }

  public void setName(final String newName) {
    myParent.removeChild(this);
    myName = newName;
    myParent.addChild(this);
  }

  public void setParent(final VirtualFile newParent) {
    myParent.removeChild(this);
    myParent = (VirtualDirectoryImpl)newParent;
    myParent.addChild(this);
  }

  public boolean isInLocalFileSystem() {
    return getFileSystem() instanceof LocalFileSystem;
  }

  public void invalidate() {
    myId = 0;
  }

  public Charset getCharset() {
    if (!isDirectory() && !isCharsetSet()) {
      try {
        LoadTextUtil.detectCharset(this, contentsToByteArray());
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return super.getCharset();
  }

  public String getPresentableName() {
    if (UISettings.getInstance().HIDE_KNOWN_EXTENSION_IN_TABS) {
      final String nameWithoutExtension = getNameWithoutExtension();
      return nameWithoutExtension.length() == 0 ? getName() : nameWithoutExtension;
    }
    return getName();
  }
}