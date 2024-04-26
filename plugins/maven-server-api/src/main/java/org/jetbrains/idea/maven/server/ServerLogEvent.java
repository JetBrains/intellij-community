// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  private final Type myType;
  private final String myString;

  @NotNull
  public Type getType() {
    return myType;
  }

  public String getMessage() {
    return myString;
  }
}
