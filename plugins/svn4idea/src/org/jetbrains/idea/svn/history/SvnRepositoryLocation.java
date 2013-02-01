/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;

import java.io.File;

/**
 * @author yole
 */
public class SvnRepositoryLocation implements RepositoryLocation {
  private final String myURL;

  public SvnRepositoryLocation(final String URL) {
    myURL = URL;
  }

  public String toString() {
    return myURL;
  }

  public String toPresentableString() {
    return myURL;
  }

  public String getURL() {
    return myURL;
  }

  public String getKey() {
    return myURL;
  }

  @Override
  public void onBeforeBatch() throws VcsException {
  }

  @Override
  public void onAfterBatch() {
  }

  @Nullable
  public static FilePath getLocalPath(final String fullPath, final NotNullFunction<File, Boolean> detector, final SvnVcs vcs) {
    if (vcs.getProject().isDefault()) return null;
    final SVNURL fullPathURL;
    try {
      fullPathURL = SVNURL.parseURIEncoded(fullPath);
    }
    catch (SVNException e) {
      return null;
    }
    final RootUrlInfo rootForUrl = vcs.getSvnFileUrlMapping().getWcRootForUrl(fullPath);
    if (rootForUrl != null) {
      return LocationDetector.filePathByUrlAndPath(fullPath, rootForUrl.getUrl().toString(), rootForUrl.getIoFile().getAbsolutePath(), detector);
    } else {
      final VirtualFile[] underVcs = ProjectLevelVcsManager.getInstance(vcs.getProject()).getRootsUnderVcs(vcs);
      if (underVcs.length == 0) return null;
      for (VirtualFile vf : underVcs) {
        final File ioFile = new File(vf.getPath());
        final SVNURL url = SvnUtil.getUrl(vcs, ioFile);
        if (url != null && SVNURLUtil.isAncestor(url, fullPathURL)) {
          return LocationDetector.filePathByUrlAndPath(fullPath, url.toString(), ioFile.getPath(), detector);
        }
      }
    }

    return null;
  }
}
