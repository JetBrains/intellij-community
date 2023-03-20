// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;

import java.io.File;

public class MavenCoreInitializationException extends RuntimeException {


  @Nullable
  private final MavenId myUnresolvedExtensionId;

  public MavenCoreInitializationException(@NotNull Throwable cause, @Nullable MavenId id) {
    super(cause);
    myUnresolvedExtensionId = id;
  }

  @Nullable
  public MavenId getUnresolvedExtensionId() {
    return myUnresolvedExtensionId;
  }
}
