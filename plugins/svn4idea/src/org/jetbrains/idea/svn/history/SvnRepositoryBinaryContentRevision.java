// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.BinaryContentRevision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;

/**
 * @author yole
 */
public class SvnRepositoryBinaryContentRevision extends SvnRepositoryContentRevision implements BinaryContentRevision {
  public SvnRepositoryBinaryContentRevision(@NotNull SvnVcs vcs, @NotNull FilePath remotePath, @Nullable FilePath localPath, long revision) {
    super(vcs, remotePath, localPath, revision);
  }

  @Override
  @Nullable
  public byte[] getBinaryContent() throws VcsException {
    return getContentAsBytes();
  }
}
