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
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.browse.DirectoryEntry;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SvnRepositoryCache {
  private final Map<String, List<DirectoryEntry>> myMap = ContainerUtil.createSoftMap();
  private final Map<String, String> myErrorsMap = ContainerUtil.createSoftMap();

  public static SvnRepositoryCache getInstance() {
    return ServiceManager.getService(SvnRepositoryCache.class);
  }
  
  private SvnRepositoryCache() {
  }

  @Nullable
  public List<DirectoryEntry> getChildren(final String parent) {
    return myMap.get(parent);
  }

  @Nullable
  public String getError(final String parent) {
    return myErrorsMap.get(parent);
  }

  public void put(final String parent, final String error) {
    myMap.remove(parent);
    myErrorsMap.put(parent, error);
  }

  public void put(final String parent, List<DirectoryEntry> children) {
    myErrorsMap.remove(parent);
    myMap.put(parent, children);
  }

  public void remove(final String parent) {
    myErrorsMap.remove(parent);
    myMap.remove(parent);
  }

  public void clear(final String repositoryRootUrl) {
    for (Iterator<Map.Entry<String, List<DirectoryEntry>>> iterator = myMap.entrySet().iterator(); iterator.hasNext();) {
      final Map.Entry<String, List<DirectoryEntry>> entry = iterator.next();
      if (entry.getKey().startsWith(repositoryRootUrl)) {
        iterator.remove();
      }
    }
  }
}
