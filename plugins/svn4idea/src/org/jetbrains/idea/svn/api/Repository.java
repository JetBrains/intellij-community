package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @author Konstantin Kolosovsky.
 */
public class Repository {

  @NotNull private final SVNURL myUrl;

  public Repository(@NotNull SVNURL url) {
    myUrl = url;
  }

  @NotNull
  public SVNURL getUrl() {
    return myUrl;
  }
}
