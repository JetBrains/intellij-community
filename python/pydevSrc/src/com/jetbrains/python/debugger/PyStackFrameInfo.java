// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;


import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public class PyStackFrameInfo {

  private final String myThreadId;
  private final String myId;
  private final String myName;
  private final PySourcePosition myPosition;

  public PyStackFrameInfo(final String threadId, final String id, final @NlsSafe String name, final PySourcePosition position) {
    myThreadId = threadId;
    myId = id;
    myName = name;
    myPosition = position;
  }

  public @NotNull String getThreadId() {
    return myThreadId;
  }

  public String getId() {
    return myId;
  }

  public @NlsSafe String getName() {
    return myName;
  }

  public PySourcePosition getPosition() {
    return myPosition;
  }
}
