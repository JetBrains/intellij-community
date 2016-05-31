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
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author yole
 */
public abstract class PythonPathCache {
  private final Map<QualifiedName, List<SoftReference<PsiElement>>> myCache = ContainerUtil.newConcurrentMap();
  private final Map<String, List<QualifiedName>> myQNameCache = ContainerUtil.newConcurrentMap();

  public void clearCache() {
    myCache.clear();
    myQNameCache.clear();
  }

  @Nullable
  public List<PsiElement> get(QualifiedName qualifiedName) {
    final List<SoftReference<PsiElement>> references = myCache.get(qualifiedName);
    if (references == null) {
      return null;
    }
    final List<PsiElement> result = references.stream().map(r -> r.get()).filter(p -> p != null).collect(Collectors.toList());
    final boolean staleElementRemoved = result.removeIf(e -> !e.isValid());
    if (staleElementRemoved) {
      Logger.getInstance(PythonPathCache.class).warn("Removing invalid element from cache");
    }
    return (!result.isEmpty() ? result : null);
  }

  public void put(QualifiedName qualifiedName, List<PsiElement> results) {
    if (results != null) {
      myCache.put(qualifiedName, new ArrayList<>(results.stream().map(e -> new SoftReference<>(e)).collect(Collectors.toList())));
    }
  }

  @Nullable
  public List<QualifiedName> getNames(VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }
    return myQNameCache.get(vFile.getUrl());
  }

  public void putNames(VirtualFile vFile, List<QualifiedName> qNames) {
    myQNameCache.put(vFile.getUrl(), new ArrayList<>(qNames));
  }

  protected class MyVirtualFileAdapter extends VirtualFileAdapter {
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
      clearCache();
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
      clearCache();
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
      clearCache();
    }

    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
      clearCache();
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        clearCache();
      }
    }
  }
}
