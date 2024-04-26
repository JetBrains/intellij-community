// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.xdebugger.frame.XExecutionStack;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


public class PyExecutionStack extends XExecutionStack {

  private final PyDebugProcess myDebugProcess;
  private final PyThreadInfo myThreadInfo;
  private PyStackFrame myTopFrame;

  public PyExecutionStack(final @NotNull PyDebugProcess debugProcess, final @NotNull PyThreadInfo threadInfo) {
    super(threadInfo.getName()); //NON-NLS
    myDebugProcess = debugProcess;
    myThreadInfo = threadInfo;
  }

  public PyExecutionStack(final @NotNull PyDebugProcess debugProcess, final @NotNull PyThreadInfo threadInfo, final @Nullable Icon icon) {
    super(threadInfo.getName(), icon); //NON-NLS
    myDebugProcess = debugProcess;
    myThreadInfo = threadInfo;
  }

  @Override
  public PyStackFrame getTopFrame() {
    if (myTopFrame == null) {
      final List<PyStackFrameInfo> frames = myThreadInfo.getFrames();
      if (frames != null) {
        myTopFrame = convert(myDebugProcess, frames.get(0));
      }
    }
    return myTopFrame;
  }

  @Override
  public void computeStackFrames(int firstFrameIndex, XStackFrameContainer container) {
    if (myThreadInfo.getState() != PyThreadInfo.State.SUSPENDED) {
      container.errorOccurred(PyBundle.message("debugger.stack.frames.not.available.in.non.suspended.state"));
      return;
    }

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      List<PyStackFrameInfo> frames = myThreadInfo.getFrames();
      if (frames != null && firstFrameIndex <= frames.size() && !container.isObsolete()) {
        List<PyStackFrame> xFrames = new LinkedList<>();
        for (int i = firstFrameIndex; i < frames.size() && !container.isObsolete(); i++) {
          xFrames.add(convert(myDebugProcess, frames.get(i)));
        }
        container.addStackFrames(xFrames, true);
      }
      else {
        container.addStackFrames(Collections.emptyList(), true);
      }
    });
  }

  @NotNull PyThreadInfo getThreadInfo() {
    return myThreadInfo;
  }

  private static PyStackFrame convert(final PyDebugProcess debugProcess, final PyStackFrameInfo frameInfo) {
    return debugProcess.createStackFrame(frameInfo);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PyExecutionStack that = (PyExecutionStack)o;

    if (myThreadInfo != null ? !myThreadInfo.equals(that.myThreadInfo) : that.myThreadInfo != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myThreadInfo != null ? myThreadInfo.hashCode() : 0;
  }

  public String getThreadId() {
    return myThreadInfo.getId();
  }
}
