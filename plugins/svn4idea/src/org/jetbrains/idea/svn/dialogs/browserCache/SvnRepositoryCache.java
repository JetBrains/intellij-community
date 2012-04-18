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
import com.intellij.util.containers.SoftHashMap;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorMessage;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SvnRepositoryCache {
  private final Map<String, List<SVNDirEntry>> myMap;
  private final Map<String, SVNErrorMessage> myErrorsMap;

  public static SvnRepositoryCache getInstance() {
    return ServiceManager.getService(SvnRepositoryCache.class);
  }
  
  private SvnRepositoryCache() {
    myMap = new SoftHashMap<String, List<SVNDirEntry>>();
    myErrorsMap = new SoftHashMap<String, SVNErrorMessage>();
  }

  @Nullable
  public List<SVNDirEntry> getChildren(final String parent) {
    return myMap.get(parent);
  }

  @Nullable
  public SVNErrorMessage getError(final String parent) {
    return myErrorsMap.get(parent);
  }

  public void put(final String parent, final SVNErrorMessage error) {
    myMap.remove(parent);
    myErrorsMap.put(parent, error);
  }

  public void put(final String parent, List<SVNDirEntry> children) {
    myErrorsMap.remove(parent);
    myMap.put(parent, children);
  }

  public void remove(final String parent) {
    myErrorsMap.remove(parent);
    myMap.remove(parent);
  }

  public void clear(final String repositoryRootUrl) {
    for (Iterator<Map.Entry<String, List<SVNDirEntry>>> iterator = myMap.entrySet().iterator(); iterator.hasNext();) {
      final Map.Entry<String, List<SVNDirEntry>> entry = iterator.next();
      if (entry.getKey().startsWith(repositoryRootUrl)) {
        iterator.remove();
      }
    }
  }
}
