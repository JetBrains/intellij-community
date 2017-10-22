// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.conflict;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseNodeDescription;
import org.jetbrains.idea.svn.api.NodeKind;
import org.jetbrains.idea.svn.api.Url;

public class ConflictVersion extends BaseNodeDescription {

  private final Url myRepositoryRoot;
  private final String myPath;
  private final long myPegRevision;
  public ConflictVersion(Url repositoryRoot, String path, long pegRevision, @NotNull NodeKind kind) {
    super(kind);
    myRepositoryRoot = repositoryRoot;
    myPath = path;
    myPegRevision = pegRevision;
  }

  public Url getRepositoryRoot() {
    return myRepositoryRoot;
  }

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
    StringBuilder urlBuilder = new StringBuilder();

    urlBuilder.append(myRepositoryRoot != null ? myRepositoryRoot : "");
    urlBuilder.append("/");
    urlBuilder.append(myPath != null ? myPath : "...");

    return "(" + getKind() + ") " + urlBuilder + "@" + getPegRevision();
  }

  @NotNull
  public static String toPresentableString(@Nullable ConflictVersion version) {
    return version == null ? "" : version.toPresentableString();
  }
}
