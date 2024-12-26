// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public class ServerLogEvent implements Serializable {
  public ServerLogEvent(@NotNull Type type, String string) {
    myType = type;
    myString = string;
  }

  public enum Type {
    INFO,
    WARN,
    ERROR,
    PRINT,
    DEBUG
  }

  private final @NotNull Type myType;
  private final String myString;

  public @NotNull Type getType() {
    return myType;
  }

  public String getMessage() {
    return myString;
  }
}
