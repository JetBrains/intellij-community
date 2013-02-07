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
  private final Set<MyPointInfo> mySet;
  private final Project myProject;
  private final SvnFileUrlMapping myMapping;

  public NestedCopiesBuilder(final Project project, final SvnFileUrlMapping mapping) {
    myProject = project;
    myMapping = mapping;
    mySet = new HashSet<MyPointInfo>();
  }

  public void process(final FilePath path, final SVNStatus status) throws SVNException {
    if ((path.getVirtualFile() != null) && SvnVcs.svnStatusIs(status, SVNStatusType.STATUS_EXTERNAL)) {
      final MyPointInfo info = new MyPointInfo(path.getVirtualFile(), null, WorkingCopyFormat.UNKNOWN, NestedCopyType.external, null);
      mySet.add(info);
      return;
    }
    if ((path.getVirtualFile() == null) || (status.getURL() == null)) return;

    final NestedCopyType type;
    if (SvnVcs.svnStatusIsUnversioned(status)) {
      return;
    } else if (status.isSwitched()) {
      type = NestedCopyType.switched;
    } else {
      return;
    }
    final MyPointInfo info = new MyPointInfo(path.getVirtualFile(), status.getURL(),
                                             WorkingCopyFormat.getInstance(status.getWorkingCopyFormat()), type, status.getRepositoryRootURL());
    mySet.add(info);
  }

  public void processIgnored(final VirtualFile vFile) {
  }

  public void processUnversioned(final VirtualFile vFile) {
  }

  @Override
  public void processCopyRoot(VirtualFile file, SVNURL url, WorkingCopyFormat format, SVNURL rootURL) {
    final MyPointInfo info = new MyPointInfo(file, url, format, NestedCopyType.inner, rootURL);
    mySet.add(info);
  }

  @Override
  public void bewareRoot(VirtualFile vf, SVNURL url, WorkingCopyFormat copyFormat) {
    final File ioFile = new File(vf.getPath());
    final RootUrlInfo info = myMapping.getWcRootForFilePath(ioFile);
    if (info != null && FileUtil.filesEqual(ioFile, info.getIoFile()) && ! info.getAbsoluteUrlAsUrl().equals(url)) {
      SvnVcs.getInstance(myProject).invokeRefreshSvnRoots();
    }
  }

  static class MyPointInfo {
    private final VirtualFile myFile;
    private SVNURL myUrl;
    private WorkingCopyFormat myFormat;
    private final NestedCopyType myType;
    private SVNURL myRootURL;

    MyPointInfo(@NotNull final VirtualFile file,
                final SVNURL url,
                final WorkingCopyFormat format,
                final NestedCopyType type,
                SVNURL rootURL) {
      myFile = file;
      myUrl = url;
      myFormat = format;
      myType = type;
      myRootURL = rootURL;
    }

    public void setUrl(SVNURL url) {
      myUrl = url;
    }

    public SVNURL getRootURL() {
      return myRootURL;
    }

    public void setFormat(WorkingCopyFormat format) {
      myFormat = format;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public SVNURL getUrl() {
      return myUrl;
    }

    public WorkingCopyFormat getFormat() {
      return myFormat;
    }

    public NestedCopyType getType() {
      return myType;
    }

    private String key(final VirtualFile file) {
      return file.getPath();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyPointInfo info = (MyPointInfo)o;

      if (! key(myFile).equals(key(info.myFile))) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return key(myFile).hashCode();
    }

    public void setRootURL(final SVNURL value) {
      myRootURL = value;
    }
  }

  public Set<MyPointInfo> getSet() {
    return mySet;
  }
}
