package org.jetbrains.idea.svn;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.AbstractVcs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface SvnFileUrlMapping extends AbstractVcs.RootsConvertor {
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

  VirtualFile[] getNotFilteredRoots();
}
