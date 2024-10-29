// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.javaee;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class ExternalResourcesRootsProvider extends IndexableSetContributor {
  private final CachedValue<Set<VirtualFile>> myStandardResources = new CachedValueImpl<>(() -> {
    ExternalResourceManagerExImpl manager = (ExternalResourceManagerExImpl)ExternalResourceManager.getInstance();
    Set<String> duplicateCheck = new HashSet<>();
    Set<VirtualFile> set = new HashSet<>();
    for (Map<String, ExternalResource> map : manager.getStandardResources$intellij_xml_psi_impl()) {
      for (ExternalResource resource : map.values()) {
        String url = resource.getResourceUrl();
        if (url != null) {
          url = url.substring(0, url.lastIndexOf('/') + 1);
          if (duplicateCheck.add(url)) {
            VirtualFile file = VfsUtilCore.findRelativeFile(url, null);
            if (file != null) {
              set.add(file);
            }
          }
        }
      }
    }
    return CachedValueProvider.Result.create(set, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS);
  });

  @Override
  public @NotNull Set<VirtualFile> getAdditionalRootsToIndex() {
    Set<VirtualFile> standardResources = myStandardResources.getValue();
    Set<VirtualFile> roots = new HashSet<>(standardResources.size() + 1);
    roots.addAll(standardResources);
    String path = FetchExtResourceAction.getExternalResourcesPath();
    VirtualFile extResources = LocalFileSystem.getInstance().findFileByPath(path);
    if (extResources != null) {
      roots.add(extResources);
    }
    return roots;
  }
}
