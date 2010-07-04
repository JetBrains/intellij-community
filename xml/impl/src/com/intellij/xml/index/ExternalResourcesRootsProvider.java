/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xml.index;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ExternalResourcesRootsProvider extends IndexableSetContributor {
  private static final URL ourRoot = ExternalResourcesRootsProvider.class.getResource(ExternalResourceManagerImpl.STANDARD_SCHEMAS);

  @Nullable
  private static VirtualFile getStandardSchemas() {
    return ourRoot == null ? null : VfsUtil.findFileByURL(ourRoot);
  }

  public Set<VirtualFile> getAdditionalRootsToIndex() {
    final VirtualFile standardSchemas = getStandardSchemas();
    String path = FetchExtResourceAction.getExternalResourcesPath();
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile extResources = localFileSystem.findFileByPath(path);
    HashSet<VirtualFile> roots = new HashSet<VirtualFile>(2);
    if (standardSchemas != null) {
      roots.add(standardSchemas);
    }
    if (extResources != null) {
      roots.add(extResources);
    }
    return roots;
  }
}
