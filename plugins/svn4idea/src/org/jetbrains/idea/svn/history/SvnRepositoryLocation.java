package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import org.jetbrains.annotations.Nullable;

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

  @Nullable
  protected FilePath detectWhenNoRoot(final String fullPath) {
    return null;
  }

  @Nullable
  public FilePath getLocalPath(final String fullPath) {
    if (myRootFile == null) {
      return detectWhenNoRoot(fullPath);
    }

    if (fullPath.startsWith(myURL)) {
      return LocationDetector.filePathByUrlAndPath(fullPath, myURL, myRootFile.getPresentableUrl());
    }
    return null;
  }
}
