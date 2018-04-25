// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.conflict;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Url;

public class ConflictVersion extends BaseNodeDescription {

  @NotNull private final Url myRepositoryRoot;
  @NotNull private final String myPath;
  private final long myPegRevision;

  public ConflictVersion(@NotNull Url repositoryRoot, @NotNull String path, long pegRevision, @NotNull NodeKind kind) {
    super(kind);
    myRepositoryRoot = repositoryRoot;
    myPath = path;
    myPegRevision = pegRevision;
  }

  @NotNull
  public Url getRepositoryRoot() {
    return myRepositoryRoot;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  public long getPegRevision() {
    return myPegRevision;
  }

  @NotNull
  public NodeKind getKind() {
    return myKind;
  }

  @NotNull
  public String toPresentableString() {
    return "(" + getKind() + ") " + myRepositoryRoot.toDecodedString() + "/" + myPath + "@" + getPegRevision();
  }

  @NotNull
  public static String toPresentableString(@Nullable ConflictVersion version) {
    return version == null ? "" : version.toPresentableString();
  }
}
