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
import org.jetbrains.idea.svn.SvnFileUrlMapping;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import java.util.Map;

/**
* @author Konstantin Kolosovsky.
*/
public class SvnCopyPathTracker {

  private static final Logger LOG = Logger.getInstance(SvnCopyPathTracker.class);

  @NotNull private String myCurrentPath;
  private String myRepositoryRoot;
  private boolean myHadChanged;

  public SvnCopyPathTracker(@NotNull String repositoryUrl, @NotNull String relativeUrl) {
    myRepositoryRoot = repositoryUrl;
    myCurrentPath = relativeUrl;
  }

  public void accept(@NotNull final LogEntry entry) {
    final Map changedPaths = entry.getChangedPaths();
    if (changedPaths == null) return;

    for (Object o : changedPaths.values()) {
      final LogEntryPath entryPath = (LogEntryPath) o;
      if (entryPath != null && 'A' == entryPath.getType() && entryPath.getCopyPath() != null) {
        if (myCurrentPath.equals(entryPath.getPath())) {
          myHadChanged = true;
          myCurrentPath = entryPath.getCopyPath();
          return;
        } else if (SVNPathUtil.isAncestor(entryPath.getPath(), myCurrentPath)) {
          final String relativePath = SVNPathUtil.getRelativePath(entryPath.getPath(), myCurrentPath);
          myCurrentPath = SVNPathUtil.append(entryPath.getCopyPath(), relativePath);
          myHadChanged = true;
          return;
        }
      }
    }
  }

  @Nullable
  public FilePath getFilePath(final SvnVcs vcs) {
    if (! myHadChanged) return null;
    final SvnFileUrlMapping svnFileUrlMapping = vcs.getSvnFileUrlMapping();
    final String absolutePath = SVNPathUtil.append(myRepositoryRoot, myCurrentPath);
    final String localPath = svnFileUrlMapping.getLocalPath(absolutePath);
    if (localPath == null) {
      LOG.info("Cannot find local path for url: " + absolutePath);
      return null;
    }
    return VcsUtil.getFilePath(localPath, false);
  }
}
