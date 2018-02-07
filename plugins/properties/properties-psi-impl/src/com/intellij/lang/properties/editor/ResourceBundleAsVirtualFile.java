// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.editor;

import com.intellij.ide.presentation.Presentation;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.VirtualFileWithoutContent;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @author Alexey
 */
@Presentation(icon = "AllIcons.Nodes.ResourceBundle")
public class ResourceBundleAsVirtualFile extends VirtualFile implements VirtualFileWithoutContent {
  private final ResourceBundle myResourceBundle;

  public ResourceBundleAsVirtualFile(@NotNull final ResourceBundle resourceBundle) {
    myResourceBundle = resourceBundle;
  }

  @NotNull
  public ResourceBundle getResourceBundle() {
    return myResourceBundle;
  }

  @Override
  @NotNull
  public VirtualFileSystem getFileSystem() {
    return LocalFileSystem.getInstance();
  }

  @Override
  @NotNull
  public String getPath() {
    return getName();
  }

  @Override
  @NotNull
  public String getName() {
    return myResourceBundle.isValid() ? myResourceBundle.getBaseName() : "";
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ResourceBundleAsVirtualFile resourceBundleAsVirtualFile = (ResourceBundleAsVirtualFile)o;

    if (!myResourceBundle.equals(resourceBundleAsVirtualFile.myResourceBundle)) return false;

    return true;
  }

  public int hashCode() {
    return myResourceBundle.hashCode();
  }

  @Override
  public void rename(Object requestor, @NotNull String newName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isValid() {
    if (myResourceBundle instanceof ResourceBundleImpl && !myResourceBundle.isValid()) {
      return false;
    }
    for (PropertiesFile propertiesFile : myResourceBundle.getPropertiesFiles()) {
      final VirtualFile virtualFile = propertiesFile.getVirtualFile();
      if (virtualFile == null || !virtualFile.isValid()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return myResourceBundle.getBaseDirectory();
  }

  @Override
  public VirtualFile[] getChildren() {
    return EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public VirtualFile createChildDirectory(Object requestor, @NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFile createChildData(Object requestor, @NotNull String name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(Object requestor) {
    //todo
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) {
    //todo
  }

  @Override
  public InputStream getInputStream() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() {
    //TODO compare files action uses this method
    return new byte[0];
  }

  @Override
  public long getModificationStamp() {
    return 0;
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  public long getLength() {
    return 0;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    List<VirtualFile> files = ContainerUtil.mapNotNull(myResourceBundle.getPropertiesFiles(), file -> file.getVirtualFile());
    if (!files.isEmpty()) {
      RefreshQueue.getInstance().refresh(false, false, postRunnable, files);
    }
  }
}
