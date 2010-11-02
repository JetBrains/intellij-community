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
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.IndexableSetContributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ExternalResourcesRootsProvider extends IndexableSetContributor {

  public Set<VirtualFile> getAdditionalRootsToIndex() {

    ExternalResourceManagerImpl manager = (ExternalResourceManagerImpl)ExternalResourceManagerEx.getInstance();
    List<String> urls = manager.getStandardResources();
    Set<String> dirs = new HashSet<String>(ContainerUtil.map(urls, new Function<String, String>() {
      @Override
      public String fun(String s) {
        int endIndex = s.lastIndexOf('/');
        return endIndex > 0 ? s.substring(0, endIndex) : s;
      }
    }));

    HashSet<VirtualFile> roots = new HashSet<VirtualFile>();
    for (String url : dirs) {
      VirtualFile file = VfsUtil.findRelativeFile(url, null);
      if (file != null) {
        roots.add(file);
      }
    }

    String path = FetchExtResourceAction.getExternalResourcesPath();
    VirtualFile extResources = LocalFileSystem.getInstance().findFileByPath(path);
    ContainerUtil.addIfNotNull(extResources, roots);
    
    return roots;
  }
}
