// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import java.io.Serializable;

public class MavenServerConsoleEvent implements Serializable {

  private final int level;
  private final String message;
  private final Throwable throwable;

  public MavenServerConsoleEvent(int level, String message, Throwable throwable) {
    this.level = level;
    this.message = message;
    this.throwable = throwable;
  }

  public int getLevel() {
    return level;
  }

  public String getMessage() {
    return message;
  }

  public Throwable getThrowable() {
    return throwable;
  }
}
