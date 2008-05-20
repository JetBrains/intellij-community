package org.jetbrains.idea.svn.history;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class LocationDetector {
  private final Map<String, File> myMap;

  public LocationDetector(final SvnVcs vcs) {
    myMap = new HashMap<String, File>();

    final ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    final VirtualFile[] roots = ProjectLevelVcsManager.getInstance(vcs.getProject()).getRootsUnderVcs(vcs);

    for (VirtualFile root : roots) {
      myMap.putAll(SvnUtil.getLocationInfoForModule(vcs, new File(root.getPath()), progress));
    }
  }

  @Nullable
  public FilePath crawlForPath(final String fullPath, final NotNullFunction<File, Boolean> detector) {
    for (Map.Entry<String, File> entry : myMap.entrySet()) {
      final String url = entry.getKey();
      if (SVNPathUtil.isAncestor(url, fullPath)) {
        return filePathByUrlAndPath(fullPath, url, entry.getValue().getAbsolutePath(), detector);
      }
    }
    return null;
  }

  static FilePath filePathByUrlAndPath(final String longPath, final String parentUrl, final String parentPath, final NotNullFunction<File, Boolean> detector) {
    final String relPath = longPath.substring(parentUrl.length());
    final File localFile = new File(parentPath, relPath);
    return VcsContextFactory.SERVICE.getInstance().createFilePathOn(localFile, detector);
  }
}
