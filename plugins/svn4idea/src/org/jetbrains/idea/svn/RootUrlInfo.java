package org.jetbrains.idea.svn;

import org.tmatesoft.svn.core.SVNURL;

public class RootUrlInfo {
  private final SVNURL myRepositoryUrlUrl;
  private final String myRepositoryUrl;
  private final SVNURL myAbsoluteUrlAsUrl;
  private final WorkingCopyFormat myFormat;

  public RootUrlInfo(final SVNURL repositoryUrl, final SVNURL absoluteUrlAsUrl, final WorkingCopyFormat format) {
    myRepositoryUrlUrl = repositoryUrl;
    myFormat = format;
    final String asString = repositoryUrl.toString();
    myRepositoryUrl = asString.endsWith("/") ? asString.substring(0, asString.length() - 1) : asString;
    myAbsoluteUrlAsUrl = absoluteUrlAsUrl;
  }

  public String getRepositoryUrl() {
    return myRepositoryUrl;
  }

  public SVNURL getRepositoryUrlUrl() {
    return myRepositoryUrlUrl;
  }

  public String getAbsoluteUrl() {
    return myAbsoluteUrlAsUrl.toString();
  }

  public SVNURL getAbsoluteUrlAsUrl() {
    return myAbsoluteUrlAsUrl;
  }

  public WorkingCopyFormat getFormat() {
    return myFormat;
  }
}
