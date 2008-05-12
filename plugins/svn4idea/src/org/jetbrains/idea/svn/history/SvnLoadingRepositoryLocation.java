package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

public class SvnLoadingRepositoryLocation extends SvnRepositoryLocation {
  private final LocationDetector locationDetector;

  public SvnLoadingRepositoryLocation(final FilePath rootFile, @NotNull final String URL, @NotNull final SvnVcs vcs) {
    super(rootFile, URL);
    locationDetector = new LocationDetector(vcs);
  }

  public SvnLoadingRepositoryLocation(@NotNull final String URL, @NotNull final SvnVcs vcs) {
    super(URL);
    locationDetector = new LocationDetector(vcs);
  }

  protected FilePath detectWhenNoRoot(final String fullPath) {
    return locationDetector.crawlForPath(fullPath);
  }
}
