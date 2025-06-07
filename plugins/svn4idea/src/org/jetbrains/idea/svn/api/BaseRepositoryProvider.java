// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnVcs;

public abstract class BaseRepositoryProvider implements RepositoryProvider {

  protected final @NotNull SvnVcs myVcs;
  protected final @NotNull Target myTarget;

  public BaseRepositoryProvider(@NotNull SvnVcs vcs, @NotNull Target target) {
    myVcs = vcs;
    myTarget = target;
  }
}
