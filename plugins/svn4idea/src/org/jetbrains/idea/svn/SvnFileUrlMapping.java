package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;
import java.util.List;

public interface SvnFileUrlMapping extends AbstractVcs.RootsConvertor {
  @Nullable
  SVNURL getUrlForFile(final File file);

  @Nullable
  String getLocalPath(final String url);

  @Nullable
  RootUrlInfo getWcRootForUrl(final String url);

  List<RootUrlInfo> getAllWcInfos();

  @Nullable
  RootUrlInfo getWcRootForFilePath(final File file);

  /**
   * @return true if roots under SVN set by the user differs from real WC roots (are under specified roots)
   */
  boolean rootsDiffer();

  VirtualFile[] getNotFilteredRoots();

  boolean isEmpty();
}
