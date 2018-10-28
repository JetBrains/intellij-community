// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browserCache;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.browse.DirectoryEntry;

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
}
