/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
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

  public PyExecutionStack(@NotNull final PyDebugProcess debugProcess, @NotNull final PyThreadInfo threadInfo) {
    super(threadInfo.getName());
    myDebugProcess = debugProcess;
    myThreadInfo = threadInfo;
  }

  public PyExecutionStack(@NotNull final PyDebugProcess debugProcess, @NotNull final PyThreadInfo threadInfo, final @Nullable Icon icon) {
    super(threadInfo.getName(), icon);
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
      container.errorOccurred("Frames not available in non-suspended state");
      return;
    }

    final List<PyStackFrameInfo> frames = myThreadInfo.getFrames();
    if (frames != null && firstFrameIndex <= frames.size()) {
      final List<PyStackFrame> xFrames = new LinkedList<>();
      for (int i = firstFrameIndex; i < frames.size(); i++) {
        xFrames.add(convert(myDebugProcess, frames.get(i)));
      }
      container.addStackFrames(xFrames, true);
    }
    else {
      container.addStackFrames(Collections.<XStackFrame>emptyList(), true);
    }
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
