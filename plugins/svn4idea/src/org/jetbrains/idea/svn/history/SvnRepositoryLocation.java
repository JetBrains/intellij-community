package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootMixedInfo;
import org.jetbrains.idea.svn.SvnVcs;

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
  public static FilePath getLocalPath(final String fullPath, final NotNullFunction<File, Boolean> detector, final SvnVcs vcs) {
    final RootMixedInfo rootForUrl = vcs.getSvnFileUrlMapping().getWcRootForUrl(fullPath);
    if (rootForUrl != null) {
      return LocationDetector.filePathByUrlAndPath(fullPath, rootForUrl.getUrl().toString(), rootForUrl.getFilePath(), detector);
    }

    return null;
  }
}
