// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

import java.util.Collection;

public class SvnRepositoryLocationGroup extends RepositoryLocationGroup {
  private final Url myUrl;

  public SvnRepositoryLocationGroup(@NotNull final Url url, final Collection<RepositoryLocation> locations) {
    super(url.toString());
    myUrl = url;
    for (RepositoryLocation location : locations) {
      add(location);
    }
  }

  public Url getUrl() {
    return myUrl;
  }
}
