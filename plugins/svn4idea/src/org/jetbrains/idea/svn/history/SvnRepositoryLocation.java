package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;

/**
 * @author yole
 */
public class SvnRepositoryLocation implements RepositoryLocation {
  private FilePath myRootFile;
  private String myURL;

  public SvnRepositoryLocation(final FilePath rootFile, final String URL) {
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

  public FilePath getRootFile() {
    return myRootFile;
  }

  public String getURL() {
    return myURL;
  }
}
