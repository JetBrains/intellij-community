/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.reference.SoftReference;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class PythonPathCache {
  private final Map<QualifiedName, SoftReference<List<PsiElement>>> myCache = ContainerUtil.newConcurrentMap();
  private final Map<String, List<QualifiedName>> myQNameCache = ContainerUtil.newConcurrentMap();

  public void clearCache() {
    myCache.clear();
    myQNameCache.clear();
  }

  /***
   * @return null if nothing found in cache. If path resolves to nothing you get empty list
   */
  @Nullable
  public List<PsiElement> get(@NotNull final QualifiedName qualifiedName) {
    final SoftReference<List<PsiElement>> references = myCache.get(qualifiedName);
    if (references == null) {
      return null;
    }
    final List<PsiElement> elements = references.get();
    if(elements != null && ! elements.stream().allMatch(PsiElement::isValid)) {
      // At least one element is invalid
      return null;
    }
    return elements != null ? Collections.unmodifiableList(elements) : null;
  }

  public void put(QualifiedName qualifiedName, List<PsiElement> results) {
    if (results != null) {
      myCache.put(qualifiedName, new SoftReference<>(results));
    }
  }

  @Nullable
  public List<QualifiedName> getNames(VirtualFile vFile) {
    if (vFile == null) {
      return null;
    }
    final List<QualifiedName> names = myQNameCache.get(vFile.getUrl());
    return names != null ? Collections.unmodifiableList(names) : null;
  }

  public void putNames(VirtualFile vFile, List<QualifiedName> qNames) {
    myQNameCache.put(vFile.getUrl(), new ArrayList<>(qNames));
  }

  protected class MyVirtualFileListener implements VirtualFileListener {
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
