// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ContentRevision;
import org.jetbrains.annotations.NotNull;

public abstract class SvnBaseContentRevision implements ContentRevision {

  protected final @NotNull SvnVcs myVcs;
  protected final @NotNull FilePath myFile;

  protected SvnBaseContentRevision(@NotNull SvnVcs vcs, @NotNull FilePath file) {
    myVcs = vcs;
    myFile = file;
  }

  @Override
  public @NotNull FilePath getFile() {
    return myFile;
  }
}
