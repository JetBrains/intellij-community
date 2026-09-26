// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

import java.util.Collection;

public class SvnRepositoryLocationGroup extends RepositoryLocationGroup {
  private final Url myUrl;

  public SvnRepositoryLocationGroup(final @NotNull Url url, final Collection<RepositoryLocation> locations) {
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
