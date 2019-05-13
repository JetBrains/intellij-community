// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.config;

import org.jetbrains.annotations.NotNull;

public class CompositeRunnable implements Runnable {
  private final Runnable[] myRunnables;

  public CompositeRunnable(@NotNull Runnable... runnables) {
    myRunnables = runnables;
  }

  @Override
  public void run() {
    for (Runnable runnable : myRunnables) {
      runnable.run();
    }
  }
}
