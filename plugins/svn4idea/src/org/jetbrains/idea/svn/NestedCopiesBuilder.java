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
package org.jetbrains.idea.svn;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class NestedCopiesBuilder implements StatusReceiver {
  private final Set<NestedCopyInfo> mySet;
  private final Project myProject;
  private final SvnFileUrlMapping myMapping;
  @NotNull private final SvnVcs myVcs;

  public NestedCopiesBuilder(@NotNull final SvnVcs vcs, final SvnFileUrlMapping mapping) {
    myVcs = vcs;
    myProject = vcs.getProject();
    myMapping = mapping;
    mySet = new HashSet<NestedCopyInfo>();
  }

  public void process(final FilePath path, final SVNStatus status) throws SVNException {
    VirtualFile file = path.getVirtualFile();
    if (file != null && SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_EXTERNAL)) {
      // We do not determine here url, repository url - because url, repository url in status will determine location in the
      // repository where folder is located and not where svn:externals property points. We want the later parameters - they'll
      // determined while creating RootUrlInfos later. Format will be also determined later.
      // TODO: Probably we could move that logic here.
      final NestedCopyInfo info = new NestedCopyInfo(file, null, WorkingCopyFormat.UNKNOWN, NestedCopyType.external, null);
      mySet.add(info);
      return;
    }
    if (file == null || status.getURL() == null) return;

    if (!SvnVcs.svnStatusIsUnversioned(status) && status.isSwitched()) {
      // this one called when there is switched directory under nested working copy
      // TODO: some other cases?
      final NestedCopyInfo
        info = new NestedCopyInfo(file, status.getURL(), myVcs.getWorkingCopyFormat(path.getIOFile()), NestedCopyType.switched,
                                               status.getRepositoryRootURL());
      mySet.add(info);
    }
  }

  public void processIgnored(final VirtualFile vFile) {
  }

  public void processUnversioned(final VirtualFile vFile) {
  }

  @Override
  public void processCopyRoot(VirtualFile file, SVNURL url, WorkingCopyFormat format, SVNURL rootURL) {
    final NestedCopyInfo info = new NestedCopyInfo(file, url, format, NestedCopyType.inner, rootURL);
    mySet.add(info);
  }

  @Override
  public void bewareRoot(VirtualFile vf, SVNURL url) {
    final File ioFile = new File(vf.getPath());
    final RootUrlInfo info = myMapping.getWcRootForFilePath(ioFile);
    if (info != null && FileUtil.filesEqual(ioFile, info.getIoFile()) && ! info.getAbsoluteUrlAsUrl().equals(url)) {
      SvnVcs.getInstance(myProject).invokeRefreshSvnRoots();
    }
  }

  public Set<NestedCopyInfo> getCopies() {
    return mySet;
  }
}
