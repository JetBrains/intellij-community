// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

public interface LongRunningTask extends AutoCloseable {
  void incrementFinishedRequests();

  int getFinishedRequests();

  int getTotalRequests();

  void updateTotalRequests(int newValue);

  void cancel();

  boolean isCanceled();

  @Override
  void close();

  @NotNull
  MavenServerConsoleIndicatorImpl getIndicator();
}
