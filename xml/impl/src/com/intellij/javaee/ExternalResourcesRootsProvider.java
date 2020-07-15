// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.javaee;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ExternalResourcesRootsProvider extends IndexableSetContributor {
  private final CachedValue<Set<VirtualFile>> myStandardResources = new CachedValueImpl<>(() -> {
    ExternalResourceManagerExImpl manager = (ExternalResourceManagerExImpl)ExternalResourceManager.getInstance();
    Set<ExternalResourceManagerExImpl.Resource> dirs = new HashSet<>();
    Set<VirtualFile> set = new HashSet<>();
    for (Map<String, ExternalResourceManagerExImpl.Resource> map : manager.getStandardResources()) {
      for (ExternalResourceManagerExImpl.Resource resource : map.values()) {
        ExternalResourceManagerExImpl.Resource dir = new ExternalResourceManagerExImpl.Resource(resource.directoryName(), resource);
        if (dirs.add(dir)) {
          String url = resource.getResourceUrl();
          if (url != null) {
            ContainerUtil.addIfNotNull(set, VfsUtilCore.findRelativeFile(url.substring(0, url.lastIndexOf('/') + 1), null));
          }
        }
      }
    }
    return CachedValueProvider.Result.create(set, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
  });

  @Override
  public @NotNull Set<VirtualFile> getAdditionalRootsToIndex() {
    Set<VirtualFile> roots = new HashSet<>(myStandardResources.getValue());
    String path = FetchExtResourceAction.getExternalResourcesPath();
    VirtualFile extResources = LocalFileSystem.getInstance().findFileByPath(path);
    if (extResources != null) {
      roots.add(extResources);
    }
    return roots;
  }
}
