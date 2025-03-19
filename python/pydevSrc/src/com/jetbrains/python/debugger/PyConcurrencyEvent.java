// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public abstract class PyConcurrencyEvent {
  protected final @NotNull String myThreadId;
  protected final @NotNull String myPid;
  protected final @NotNull String myName;
  protected @NotNull EventType myType;
  protected @NotNull String myFileName;
  protected @NotNull Integer myLine;
  protected final boolean myIsAsyncio;
  protected long myTime; // microseconds
  protected @NotNull List<PyStackFrameInfo> myFrames;

  public enum EventType {
    CREATE, ACQUIRE_BEGIN, ACQUIRE_END, RELEASE, START, JOIN, STOP
  }

  public PyConcurrencyEvent(long time, @NotNull String threadId, @NotNull String name, boolean isAsyncio) {
    myTime = time;
    myThreadId = threadId;
    myPid = threadId.split("_")[0];
    myName = name;
    myIsAsyncio = isAsyncio;
    myType = EventType.CREATE;
    myFileName = "";
    myLine = 0;
    myFrames = new ArrayList<>();
  }

  public void setType(@NotNull EventType type) {
    myType = type;
  }

  public @NotNull String getThreadId() {
    return myThreadId;
  }

  public @NotNull EventType getType() {
    return myType;
  }

  public @NotNull String getThreadName() {
    return myName;
  }

  public abstract @NotNull String getEventActionName();

  public abstract boolean isThreadEvent();

  public void setFileName(@NotNull String fileName) {
    myFileName = fileName;
  }

  public @NotNull String getFileName() {
    return myFileName;
  }

  public void setLine(@NotNull Integer line) {
    myLine = line;
  }

  public long getTime() {
    return myTime;
  }

  public void setTime(long time) {
    myTime = time;
  }

  public @NotNull Integer getLine() {
    return myLine;
  }

  public @NotNull String getPid() {
    return myPid;
  }

  public @NotNull List<PyStackFrameInfo> getFrames() {
    return myFrames;
  }

  public void setFrames(@NotNull List<PyStackFrameInfo> frames) {
    myFrames = frames;
  }

  public boolean isAsyncio() {
    return myIsAsyncio;
  }
}
