// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;


import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public class PyStackFrameInfo {

  private final String myThreadId;
  private final String myId;
  private final String myName;
  private final PySourcePosition myPosition;

  public PyStackFrameInfo(final String threadId, final String id, @NlsSafe final String name, final PySourcePosition position) {
    myThreadId = threadId;
    myId = id;
    myName = name;
    myPosition = position;
  }

  @NotNull
  public String getThreadId() {
    return myThreadId;
  }

  public String getId() {
    return myId;
  }

  @NlsSafe
  public String getName() {
    return myName;
  }

  public PySourcePosition getPosition() {
    return myPosition;
  }
}
