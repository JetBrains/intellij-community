// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;

public class MavenIndexUpdateState implements Serializable {
  public @NotNull String myUrl;
  public @Nullable String myError;
  public @Nullable String myProgressInfo;
  public @NotNull State myState;
  public long timestamp;
  public double fraction;

  public MavenIndexUpdateState(@NotNull String url, @Nullable String error, @Nullable String progressInfo, @NotNull State state) {
    myUrl = url;
    myError = error;
    myProgressInfo = progressInfo;
    myState = state;
    timestamp = System.currentTimeMillis();
  }

  public void updateTimestamp() {
    timestamp = System.currentTimeMillis();
  }

  public enum State {
    INDEXING,
    SUCCEED,
    CANCELLED,
    FAILED
  }
}
