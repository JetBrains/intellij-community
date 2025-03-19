// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.Revision;

class SvnBinaryContentRevision extends SvnContentRevision implements BinaryContentRevision {

  SvnBinaryContentRevision(@NotNull SvnVcs vcs, @NotNull FilePath file, @NotNull Revision revision, boolean useBaseRevision) {
    super(vcs, file, revision, useBaseRevision);
  }

  @Override
  public byte @Nullable [] getBinaryContent() throws VcsException {
    return getContentAsBytes();
  }

  @Override
  public @NonNls String toString() {
    return "SvnBinaryContentRevision:" + myFile;
  }
}
