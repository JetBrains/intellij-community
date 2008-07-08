package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface SvnFileUrlMapping {
  @Nullable
  SVNURL getUrlForFile(final File file);

  @Nullable
  String getLocalPath(final String url);

  @NotNull
  List<VirtualFile> getWcRootsUnderVcsRoot(final VirtualFile vcsRoot);

  @Nullable
  VirtualFile getVcRootByUrl(final String url);

  @Nullable
  RootMixedInfo getWcRootForUrl(final String url);

  Map<String,RootUrlInfo> getAllWcInfos();

  @Nullable
  Pair<String, RootUrlInfo> getWcRootForFilePath(final File file);

  /**
   * @return true if roots under SVN set by the user differs from real WC roots (are under specified roots)
   */
  boolean rootsDiffer();

  class RootUrlInfo {
    private final String myRepositoryUrl;
    private final SVNURL myAbsoluteUrlAsUrl;

    public RootUrlInfo(final String repositoryUrl, final SVNURL absoluteUrlAsUrl) {
      myRepositoryUrl = repositoryUrl.endsWith("/") ? repositoryUrl.substring(0, repositoryUrl.length() - 1) : repositoryUrl;
      myAbsoluteUrlAsUrl = absoluteUrlAsUrl;
    }

    public String getRepositoryUrl() {
      return myRepositoryUrl;
    }

    public String getAbsoluteUrl() {
      return myAbsoluteUrlAsUrl.toString();
    }

    public SVNURL getAbsoluteUrlAsUrl() {
      return myAbsoluteUrlAsUrl;
    }
  }

}
