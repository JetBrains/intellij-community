package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.changes.committed.RepositoryLocationGroup;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNURL;

import java.util.Collection;

public class SvnRepositoryLocationGroup extends RepositoryLocationGroup {
  private final SVNURL myUrl;

  public SvnRepositoryLocationGroup(@NotNull final SVNURL url, final Collection<RepositoryLocation> locations) {
    super(url.toString());
    myUrl = url;
    for (RepositoryLocation location : locations) {
      add(location);
    }
  }

  public SVNURL getUrl() {
    return myUrl;
  }
}
