// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenId;

public class MavenParentDesc {
  private final MavenId myParentId;
  private final String myParentRelativePath;

  public MavenParentDesc(@NotNull MavenId parentId, @NotNull String parentRelativePath) {
    myParentId = parentId;
    myParentRelativePath = parentRelativePath;
  }

  public @NotNull MavenId getParentId() {
    return myParentId;
  }

  public @NotNull String getParentRelativePath() {
    return myParentRelativePath;
  }
}
