package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.RepositoryLocation;

/**
 * @author yole
 */
public class SvnRepositoryLocation implements RepositoryLocation {
  private VirtualFile myRootFile;
  private String myURL;

  public SvnRepositoryLocation(final VirtualFile rootFile, final String URL) {
    myRootFile = rootFile;
    myURL = URL;
  }

  public SvnRepositoryLocation(final String URL) {
    myURL = URL;
  }

  public String toString() {
    return myURL;
  }

  public String toPresentableString() {
    return myURL;
  }

  public VirtualFile getRootFile() {
    return myRootFile;
  }

  public String getURL() {
    return myURL;
  }
}
