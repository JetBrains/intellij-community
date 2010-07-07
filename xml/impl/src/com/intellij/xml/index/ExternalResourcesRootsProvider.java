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
import com.intellij.util.containers.hash.HashSet;
import com.intellij.util.indexing.IndexableSetContributor;

import java.util.List;
import java.util.Set;

/**
 * @author Dmitry Avdeev
 */
public class ExternalResourcesRootsProvider extends IndexableSetContributor {

  public Set<VirtualFile> getAdditionalRootsToIndex() {

    HashSet<VirtualFile> roots = new HashSet<VirtualFile>();

    String path = FetchExtResourceAction.getExternalResourcesPath();
    VirtualFile extResources = LocalFileSystem.getInstance().findFileByPath(path);

    ExternalResourceManagerImpl manager = (ExternalResourceManagerImpl)ExternalResourceManagerEx.getInstance();
    List<String> urls = manager.getStandardResources();
    for (String url : urls) {
      VirtualFile file = VfsUtil.findRelativeFile(url, null);
      if (file != null) {
        VirtualFile parent = file.getParent();
        if (parent != null && ("standardSchemas".equals(parent.getName()) || parent.equals(extResources))) {
          roots.add(parent);
        }
        else {
          roots.add(file);
        }
      }
    }
    
    return roots;
  }
}
