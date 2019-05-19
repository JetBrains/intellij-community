// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.info.Info;

public class InfoCommandRepositoryProvider extends BaseRepositoryProvider {

  public InfoCommandRepositoryProvider(@NotNull SvnVcs vcs, @NotNull Target target) {
    super(vcs, target);
  }

  @Nullable
  @Override
  public Repository get() {
    Repository result;

    if (myTarget.isUrl()) {
      // TODO: Also could still execute info when target is url - either to use info for authentication or to just get correct repository
      // TODO: url in case of "read" operations are allowed anonymously.
      result = new Repository(myTarget.getUrl());
    }
    else {
      Info info = myVcs.getInfo(myTarget.getFile());
      result = info != null ? new Repository(info.getRepositoryRootUrl()) : null;
    }

    return result;
  }
}
