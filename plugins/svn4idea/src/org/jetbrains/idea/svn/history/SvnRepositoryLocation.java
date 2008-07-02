package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.RootMixedInfo;

import java.io.File;

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
  protected FilePath detectWhenNoRoot(final String fullPath, final NotNullFunction<File, Boolean> detector) {
    return null;
  }

  @Nullable
  public FilePath getLocalPath(final String fullPath, final NotNullFunction<File, Boolean> detector, final SvnVcs vcs) {
    if (myRootFile == null) {
      return detectWhenNoRoot(fullPath, detector);
    }

    if (fullPath.startsWith(myURL)) {
      return LocationDetector.filePathByUrlAndPath(fullPath, myURL, myRootFile.getPresentableUrl(), detector);
    } else {
      final RootMixedInfo rootForUrl = vcs.getSvnFileUrlMapping().getWcRootForUrl(fullPath);
      if (rootForUrl != null) {
        return LocationDetector.filePathByUrlAndPath(fullPath, rootForUrl.getUrl().toString(), rootForUrl.getFilePath(), detector);
      }
    }
    return null;
  }
}
