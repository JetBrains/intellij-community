package org.jetbrains.idea.svn;

import com.intellij.openapi.vfs.VirtualFile;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

public class RootUrlInfo implements RootUrlPair {
  private final SVNURL myRepositoryUrlUrl;
  private final String myRepositoryUrl;
  private final SVNURL myAbsoluteUrlAsUrl;
  private final WorkingCopyFormat myFormat;

  private final File myIoFile;
  private final VirtualFile myVfile;
  // vcs root
  private final VirtualFile myRoot;

  public RootUrlInfo(final SVNURL repositoryUrl, final SVNURL absoluteUrlAsUrl, final WorkingCopyFormat format, final VirtualFile vfile,
                     final VirtualFile root) {
    myRepositoryUrlUrl = repositoryUrl;
    myFormat = format;
    myVfile = vfile;
    myRoot = root;
    myIoFile = new File(myVfile.getPath());
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

  public File getIoFile() {
    return myIoFile;
  }

  // vcs root
  public VirtualFile getRoot() {
    return myRoot;
  }

  public VirtualFile getVirtualFile() {
    return myVfile;
  }

  public String getUrl() {
    return myAbsoluteUrlAsUrl.toString();
  }
}
