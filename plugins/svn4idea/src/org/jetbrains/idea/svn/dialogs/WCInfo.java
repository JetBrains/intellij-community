package org.jetbrains.idea.svn.dialogs;

import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.SVNURL;

public class WCInfo {
  private final String myPath;
  private final SVNURL myUrl;
  private final WorkingCopyFormat myFormat;
  private final String myRepositoryRoot;
  private final boolean myIsWcRoot;

  public WCInfo(final String path, final SVNURL url, final WorkingCopyFormat format, final String repositoryRoot, final boolean isWcRoot) {
    myPath = path;
    myUrl = url;
    myFormat = format;
    myRepositoryRoot = repositoryRoot;
    myIsWcRoot = isWcRoot;
  }

  public String getPath() {
    return myPath;
  }

  public SVNURL getUrl() {
    return myUrl;
  }

  public WorkingCopyFormat getFormat() {
    return myFormat;
  }

  public String getRepositoryRoot() {
    return myRepositoryRoot;
  }

  public boolean isIsWcRoot() {
    return myIsWcRoot;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof WCInfo)) return false;

    final WCInfo wcInfo = (WCInfo)o;

    if (myPath != null ? !myPath.equals(wcInfo.myPath) : wcInfo.myPath != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return (myPath != null ? myPath.hashCode() : 0);
  }
}
