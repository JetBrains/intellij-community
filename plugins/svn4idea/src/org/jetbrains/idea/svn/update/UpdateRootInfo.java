// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.info.Info;

import java.io.File;

public class UpdateRootInfo {
  @Nullable private Url myUrl;
  private Revision myRevision;
  private boolean myUpdateToSpecifiedRevision = false;

  public UpdateRootInfo(File file, SvnVcs vcs) {
    myRevision = Revision.HEAD;

    Info info = vcs.getInfo(file);
    myUrl = info != null ? info.getURL() : null;
  }

  @Nullable
  public Url getUrl() {
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
