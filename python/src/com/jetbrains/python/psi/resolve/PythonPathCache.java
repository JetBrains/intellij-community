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

import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class PythonPathCache {
  private final Map<QualifiedName, List<PsiElement>> myCache = new ConcurrentHashMap<QualifiedName, List<PsiElement>>();
  private final Map<VirtualFile, List<QualifiedName>> myQNameCache = new ConcurrentHashMap<VirtualFile, List<QualifiedName>>();

  public void clearCache() {
    myCache.clear();
    myQNameCache.clear();
  }

  public List<PsiElement> get(QualifiedName qualifiedName) {
    return myCache.get(qualifiedName);
  }

  public void put(QualifiedName qualifiedName, List<PsiElement> results) {
    myCache.put(qualifiedName, results);
  }

  public List<QualifiedName> getNames(VirtualFile vFile) {
    return myQNameCache.get(vFile);
  }
  
  public void putNames(VirtualFile vFile, List<QualifiedName> qNames) {
    myQNameCache.put(vFile, qNames);
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
