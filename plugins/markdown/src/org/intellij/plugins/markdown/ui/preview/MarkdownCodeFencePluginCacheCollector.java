// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.preview;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class MarkdownCodeFencePluginCacheCollector {
  @NotNull private final VirtualFile myFile;
  @NotNull private final Collection<File> myAliveCachedFiles = new HashSet<>();

  public MarkdownCodeFencePluginCacheCollector(@NotNull VirtualFile file) {
    myFile = file;
  }

  @NotNull
  public Collection<File> getAliveCachedFiles() {
    return myAliveCachedFiles;
  }

  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  public void addAliveCachedFile(@NotNull File file) {
    myAliveCachedFiles.add(file);
  }

  //need to override `equals()`/`hasCode()` to scan cache for the latest `cacheProvider` only, see 'MarkdownCodeFencePluginCache.registerCacheProvider()'
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MarkdownCodeFencePluginCacheCollector collector = (MarkdownCodeFencePluginCacheCollector)o;
    return Objects.equals(myFile, collector.myFile);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myFile);
  }
}