// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev.dataviewer;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class DataViewerCommandBuilder {
  @Nullable private RemoteDebugger myDebugger;
  @Nullable private String myThreadId;
  @Nullable private String myFrameId;

  @NotNull private final PyDebugValue myVar;
  @NotNull private final DataViewerCommandAction myAction;
  private final String @Nullable[] myArgs;

  public DataViewerCommandBuilder(@NotNull PyDebugValue var, @NotNull DataViewerCommandAction action, String @Nullable[] args) {
    myVar = var;
    myAction = action;
    myArgs = args;
  }

  public String @Nullable[] getArgs() {
    return myArgs;
  }

  public @NotNull DataViewerCommandAction getAction() {
    return myAction;
  }

  public @Nullable RemoteDebugger getDebugger() {
    return myDebugger;
  }

  public @Nullable String getThreadId() {
    return myThreadId;
  }

  public @Nullable String getFrameId() {
    return myFrameId;
  }

  public @NotNull PyDebugValue getVar() {
    return myVar;
  }

  public void setDebugger(@NotNull RemoteDebugger debugger) {
    this.myDebugger = debugger;
  }

  public void setThreadId(@NotNull String threadId) {
    this.myThreadId = threadId;
  }

  public void setFrameId(@NotNull String frameId) {
    this.myFrameId = frameId;
  }

  public DataViewerCommand build() {
    assert myDebugger != null;
    assert myThreadId != null;
    assert myFrameId != null;

    return new DataViewerCommand(myDebugger, myThreadId, myFrameId, myVar, myAction, myArgs);
  }

  @NotNull
  public static DataViewerCommandBuilder initExportCommand(@NotNull PyDebugValue var, @NotNull File target) {
    String[] args = { target.getAbsolutePath() };
    return new DataViewerCommandBuilder(var, DataViewerCommandAction.EXPORT, args);
  }
}
