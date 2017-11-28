/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNURL;

import java.io.File;

import static org.jetbrains.idea.svn.SvnUtil.append;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.append;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.getRelativePath;
import static org.tmatesoft.svn.core.internal.util.SVNPathUtil.isAncestor;


public class SvnCopyPathTracker {

  private static final Logger LOG = Logger.getInstance(SvnCopyPathTracker.class);

  @NotNull private String myCurrentPath;
  @NotNull private final SVNURL myRepositoryUrl;
  private boolean myHadChanged;

  public SvnCopyPathTracker(@NotNull SVNURL repositoryUrl, @NotNull String repositoryRelativeUrl) {
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
        else if (isAncestor(entryPath.getPath(), myCurrentPath)) {
          myCurrentPath = append(entryPath.getCopyPath(), getRelativePath(entryPath.getPath(), myCurrentPath));
          myHadChanged = true;
          return;
        }
      }
    }
  }

  @Nullable
  public FilePath getFilePath(@NotNull SvnVcs vcs) throws SvnBindException {
    if (!myHadChanged) return null;

    SVNURL currentUrl = append(myRepositoryUrl, myCurrentPath);
    File localPath = vcs.getSvnFileUrlMapping().getLocalPath(currentUrl);

    if (localPath == null) {
      LOG.info("Cannot find local path for url: " + currentUrl);
      return null;
    }

    return VcsUtil.getFilePath(localPath, false);
  }
}
