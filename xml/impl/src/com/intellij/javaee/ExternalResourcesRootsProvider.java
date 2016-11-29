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
package com.intellij.javaee;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexableSetContributor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ExternalResourcesRootsProvider extends IndexableSetContributor {
  private final NotNullLazyValue<Set<String>> myStandardResources = new NotNullLazyValue<Set<String>>() {
    @NotNull
    @Override
    protected Set<String> compute() {
      ExternalResourceManagerExImpl manager = (ExternalResourceManagerExImpl)ExternalResourceManager.getInstance();
      Set<ExternalResourceManagerExImpl.Resource> dirs = new THashSet<>();
      Set<String> set = new THashSet<>();
      for (Map<String, ExternalResourceManagerExImpl.Resource> map : manager.getStandardResources()) {
        for (ExternalResourceManagerExImpl.Resource resource : map.values()) {
          ExternalResourceManagerExImpl.Resource dir = new ExternalResourceManagerExImpl.Resource(
            resource.directoryName(), resource);

          if (dirs.add(dir)) {
            String url = resource.getResourceUrl();
            if (url != null) {
              set.add(url.substring(0, url.lastIndexOf('/') + 1));
            }
          }
        }
      }
      return set;
    }
  };

  @NotNull
  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex() {
    Set<VirtualFile> roots = new THashSet<>();
    for (String url : myStandardResources.getValue()) {
      VirtualFile file = VfsUtilCore.findRelativeFile(url, null);
      if (file != null) {
        roots.add(file);
      }
    }

    String path = FetchExtResourceAction.getExternalResourcesPath();
    VirtualFile extResources = LocalFileSystem.getInstance().findFileByPath(path);
    ContainerUtil.addIfNotNull(roots, extResources);

    return roots;
  }
}
