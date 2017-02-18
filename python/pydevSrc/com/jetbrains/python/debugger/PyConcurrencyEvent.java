/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  public String getThreadId() {
    return myThreadId;
  }

  @NotNull
  public EventType getType() {
    return myType;
  }

  @NotNull
  public String getThreadName() {
    return myName;
  }

  @NotNull
  public abstract String getEventActionName();

  public abstract boolean isThreadEvent();

  public void setFileName(@NotNull String fileName) {
    myFileName = fileName;
  }

  @NotNull
  public String getFileName() {
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

  @NotNull
  public Integer getLine() {
    return myLine;
  }

  @NotNull
  public String getPid() {
    return myPid;
  }

  @NotNull
  public List<PyStackFrameInfo> getFrames() {
    return myFrames;
  }

  public void setFrames(@NotNull List<PyStackFrameInfo> frames) {
    myFrames = frames;
  }

  public boolean isAsyncio() {
    return myIsAsyncio;
  }
}
