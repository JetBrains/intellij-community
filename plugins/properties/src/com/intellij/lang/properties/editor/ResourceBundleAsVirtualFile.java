/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties.editor;

import com.intellij.ide.presentation.Presentation;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.ResourceBundleImpl;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * @author Alexey
 */
@Presentation(icon = "AllIcons.Nodes.ResourceBundle")
public class ResourceBundleAsVirtualFile extends VirtualFile {
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
    return myResourceBundle.getBaseName();
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
  public void rename(Object requestor, @NotNull String newName) throws IOException {
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
    if (myResourceBundle instanceof ResourceBundleImpl && !((ResourceBundleImpl)myResourceBundle).isValid()) {
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
  public VirtualFile createChildDirectory(Object requestor, @NotNull String name) throws IOException {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public VirtualFile createChildData(Object requestor, @NotNull String name) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void delete(Object requestor) throws IOException {
    //todo
  }

  @Override
  public void move(Object requestor, @NotNull VirtualFile newParent) throws IOException {
    //todo
  }

  @Override
  public InputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public byte[] contentsToByteArray() throws IOException {
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
    List<VirtualFile> files = ContainerUtil.mapNotNull(myResourceBundle.getPropertiesFiles(), new Function<PropertiesFile, VirtualFile>() {
      @Override
      public VirtualFile fun(PropertiesFile file) {
        return file.getVirtualFile();
      }
    });
    if (!files.isEmpty()) {
      RefreshQueue.getInstance().refresh(false, false, postRunnable, files);
    }
  }
}
