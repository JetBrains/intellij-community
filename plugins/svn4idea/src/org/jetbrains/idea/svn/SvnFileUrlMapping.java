package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

interface SvnFileUrlMapping {
  @Nullable
  SVNURL getUrlForFile(final String path);

  @Nullable
  String getLocalPath(final String url);

  @Nullable
  VirtualFile getVcRootByUrl(final String url);

  @Nullable
  SvnFileUrlMappingRefresher.RootMixedInfo getWcRootForUrl(final String url);

  @Nullable
  Pair<String, SvnFileUrlMappingRefresher.RootUrlInfo> getWcRootForFilePath(final String filePath);

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

  class RootMixedInfo {
    private final String myFilePath;
    private final SVNURL myUrl;
    private final VirtualFile myParentVcsRoot;

    public RootMixedInfo(final String filePath, final SVNURL url, final VirtualFile parentVcsRoot) {
      myFilePath = filePath;
      myUrl = url;
      myParentVcsRoot = parentVcsRoot;
    }

    public String getFilePath() {
      return myFilePath;
    }

    public SVNURL getUrl() {
      return myUrl;
    }

    public VirtualFile getParentVcsRoot() {
      return myParentVcsRoot;
    }
  }
}
