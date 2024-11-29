// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class MavenServerResponse<T extends Serializable> implements Serializable {
  @NotNull private final T result;
  @NotNull private final LongRunningTaskStatus status;

  public MavenServerResponse(@NotNull T result, @NotNull LongRunningTaskStatus status) {
    this.result = result;
    this.status = status;
  }

  @NotNull
  public T getResult() {
    return result;
  }

  @NotNull
  public LongRunningTaskStatus getStatus() {
    return status;
  }

}
