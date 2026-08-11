// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import java.io.File;

public class UpdateRootInfo {
  private @Nullable Url myUrl;
  private Revision myRevision;
  private boolean myUpdateToSpecifiedRevision = false;

  public UpdateRootInfo(File file, SvnVcs vcs) {
    myRevision = Revision.HEAD;
    myUrl = vcs.getSvnFileUrlMapping().getUrlForFile(file);
  }

  public @Nullable Url getUrl() {
    return myUrl;
  }

  public Revision getRevision() {
    return myRevision;
  }

  public boolean isUpdateToRevision() {
    return myUpdateToSpecifiedRevision;
  }

  public void setUrl(@NotNull Url url) {
    myUrl = url;
  }

  public void setUpdateToRevision(final boolean value) {
    myUpdateToSpecifiedRevision = value;
  }

  public void setRevision(final Revision revision) {
    myRevision = revision;
  }
}
