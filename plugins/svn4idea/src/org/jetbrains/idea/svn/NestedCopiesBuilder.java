// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.status.Status;
import org.jetbrains.idea.svn.status.StatusType;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.vcsUtil.VcsUtil.getFilePath;

public class NestedCopiesBuilder implements StatusReceiver {

  @NotNull private final Set<NestedCopyInfo> myCopies;
  @NotNull private final SvnVcs myVcs;

  public NestedCopiesBuilder(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    myCopies = new HashSet<>();
  }

  @Override
  public void process(@NotNull FilePath path, final Status status) {
    VirtualFile file = path.getVirtualFile();

    if (file != null) {
      if (status.is(StatusType.STATUS_EXTERNAL)) {
        // We do not determine here url, repository url - because url, repository url in status will determine location in the
        // repository where folder is located and not where svn:externals property points. We want the later parameters - they'll
        // determined while creating RootUrlInfos later. Format will be also determined later.
        // TODO: Probably we could move that logic here.
        myCopies.add(new NestedCopyInfo(file, null, WorkingCopyFormat.UNKNOWN, NestedCopyType.external, null));
      }
      else if (status.getUrl() != null && !status.is(StatusType.STATUS_UNVERSIONED) && status.isSwitched()) {
        // this one called when there is switched directory under nested working copy
        // TODO: some other cases?
        myCopies.add(new NestedCopyInfo(file, status.getUrl(), myVcs.getWorkingCopyFormat(path.getIOFile()), NestedCopyType.switched,
                                        status.getRepositoryRootUrl()));
      }
    }
  }

  @Override
  public void processCopyRoot(@NotNull VirtualFile file,
                              @Nullable Url url,
                              @NotNull WorkingCopyFormat format,
                              @Nullable Url rootURL) {
    myCopies.add(new NestedCopyInfo(file, url, format, NestedCopyType.inner, rootURL));
  }

  @Override
  public void bewareRoot(@NotNull VirtualFile vf, Url url) {
    final File ioFile = VfsUtilCore.virtualToIoFile(vf);
    final RootUrlInfo info = myVcs.getSvnFileUrlMapping().getWcRootForFilePath(getFilePath(vf));

    if (info != null && FileUtil.filesEqual(ioFile, info.getIoFile()) && !info.getUrl().equals(url)) {
      myVcs.invokeRefreshSvnRoots();
    }
  }

  @Override
  public void finish() {
  }

  @NotNull
  public Set<NestedCopyInfo> getCopies() {
    return myCopies;
  }
}
