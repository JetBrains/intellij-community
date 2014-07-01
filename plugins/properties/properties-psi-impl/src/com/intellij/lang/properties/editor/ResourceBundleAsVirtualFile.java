/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/**
 * @author Alexey
 */
package com.intellij.lang.properties.editor;

import com.intellij.ide.presentation.Presentation;
import com.intellij.lang.properties.PropertiesImplUtil;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@Presentation(icon = "AllIcons.Nodes.ResourceBundle")
public class ResourceBundleAsVirtualFile extends VirtualFile {
  private final VirtualFile myBasePropertiesFile;

  private ResourceBundleAsVirtualFile(@NotNull final VirtualFile basePropertiesFile) {
    myBasePropertiesFile = basePropertiesFile;
  }

  @NotNull
  public static ResourceBundleAsVirtualFile fromResourceBundle(final @NotNull ResourceBundle resourceBundle) {
    return new ResourceBundleAsVirtualFile(resourceBundle.getDefaultPropertiesFile().getVirtualFile());
  }

  @Nullable
  public ResourceBundle getResourceBundle(final Project project) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PropertiesFile file = PropertiesImplUtil.getPropertiesFile(psiManager.findFile(myBasePropertiesFile));
    if (file == null) {
      return null;
    }
    return PropertiesImplUtil.getResourceBundle(file);
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
    return PropertiesUtil.getBaseName(myBasePropertiesFile);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ResourceBundleAsVirtualFile resourceBundleAsVirtualFile = (ResourceBundleAsVirtualFile)o;

    if (!myBasePropertiesFile.equals(resourceBundleAsVirtualFile.myBasePropertiesFile)) return false;

    return true;
  }

  public int hashCode() {
    return myBasePropertiesFile.hashCode();
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
    return true;
  }

  @Override
  public VirtualFile getParent() {
    return myBasePropertiesFile.getParent();
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
    throw new UnsupportedOperationException();
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

  }
}
