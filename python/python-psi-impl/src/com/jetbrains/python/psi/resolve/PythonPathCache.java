// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileCopyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.PyCodeInsightCounters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public abstract class PythonPathCache {
  private final Map<QualifiedName, SoftReference<List<PsiElement>>> myCache = new ConcurrentHashMap<>();
  private final Map<String, List<QualifiedName>> myQNameCache = new ConcurrentHashMap<>();

  public void clearCache() {
    if (myCache.isEmpty() && myQNameCache.isEmpty()) return;
    PyCodeInsightCounters.inc(PyCodeInsightCounters.Counter.PATH_CACHE_CLEARS);
    myCache.clear();
    myQNameCache.clear();
  }

  /***
   * @return null if nothing found in cache. If path resolves to nothing you get empty list
   */
  public @Nullable List<PsiElement> get(final @NotNull QualifiedName qualifiedName) {
    final SoftReference<List<PsiElement>> references = myCache.get(qualifiedName);
    if (references == null) {
      return null;
    }
    final List<PsiElement> elements = references.get();
    if (elements != null && !elements.stream().allMatch(PsiElement::isValid)) {
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

  public @Nullable List<QualifiedName> getNames(VirtualFile vFile) {
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
