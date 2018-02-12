// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import java.io.File;

import static org.jetbrains.idea.svn.SvnUtil.append;


public class SvnCopyPathTracker {

  private static final Logger LOG = Logger.getInstance(SvnCopyPathTracker.class);

  @NotNull private String myCurrentPath;
  @NotNull private final Url myRepositoryUrl;
  private boolean myHadChanged;

  public SvnCopyPathTracker(@NotNull Url repositoryUrl, @NotNull String repositoryRelativeUrl) {
    myRepositoryUrl = repositoryUrl;
    myCurrentPath = repositoryRelativeUrl;
  }

  public void accept(@NotNull LogEntry entry) {
    for (LogEntryPath entryPath : entry.getChangedPaths().values()) {
      if (entryPath != null && 'A' == entryPath.getType() && entryPath.getCopyPath() != null) {
        if (myCurrentPath.equals(entryPath.getPath())) {
          myHadChanged = true;
          myCurrentPath = entryPath.getCopyPath();
          return;
        }
        else if (Url.isAncestor(entryPath.getPath(), myCurrentPath)) {
          myCurrentPath = Url.append(entryPath.getCopyPath(), Url.getRelative(entryPath.getPath(), myCurrentPath));
          myHadChanged = true;
          return;
        }
      }
    }
  }

  @Nullable
  public FilePath getFilePath(@NotNull SvnVcs vcs) throws SvnBindException {
    if (!myHadChanged) return null;

    Url currentUrl = append(myRepositoryUrl, myCurrentPath);
    File localPath = vcs.getSvnFileUrlMapping().getLocalPath(currentUrl);

    if (localPath == null) {
      LOG.info("Cannot find local path for url: " + currentUrl);
      return null;
    }

    return VcsUtil.getFilePath(localPath, false);
  }
}
