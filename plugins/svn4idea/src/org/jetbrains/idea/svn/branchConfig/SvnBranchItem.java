// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.api.Url;

public class SvnBranchItem implements Comparable<SvnBranchItem> {
  @NotNull private final Url myUrl;
  private final long myCreationDateMillis;
  private final long myRevision;

  public SvnBranchItem(@NotNull Url url, long creationDateMillis, long revision) {
    myUrl = url;
    myCreationDateMillis = creationDateMillis;
    myRevision = revision;
  }

  @NotNull
  public Url getUrl() {
    return myUrl;
  }

  public long getCreationDateMillis() {
    return myCreationDateMillis;
  }

  public long getRevision() {
    return myRevision;
  }

  public int compareTo(@NotNull SvnBranchItem item) {
    return Long.compare(item.myCreationDateMillis, myCreationDateMillis);
  }
}
