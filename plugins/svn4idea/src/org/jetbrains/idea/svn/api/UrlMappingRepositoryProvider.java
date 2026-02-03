// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.RootUrlInfo;
import org.jetbrains.idea.svn.SvnVcs;

import static com.intellij.vcsUtil.VcsUtil.getFilePath;

public class UrlMappingRepositoryProvider extends BaseRepositoryProvider {

  public UrlMappingRepositoryProvider(@NotNull SvnVcs vcs, @NotNull Target target) {
    super(vcs, target);
  }

  @Override
  public @Nullable Repository get() {
    RootUrlInfo rootInfo = null;

    if (!myVcs.getProject().isDefault()) {
      rootInfo = myTarget.isFile()
                 ? myVcs.getSvnFileUrlMapping().getWcRootForFilePath(getFilePath(myTarget.getFile()))
                 : myVcs.getSvnFileUrlMapping().getWcRootForUrl(myTarget.getUrl());
    }

    return rootInfo != null ? new Repository(rootInfo.getRepositoryUrl()) : null;
  }
}
