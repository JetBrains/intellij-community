// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.smartstepinto;

import com.jetbrains.python.debugger.PyStackFrame;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class PySmartStepIntoContext {
  private final int myStartLine;
  private final int myEndLine;
  @NotNull private final PyStackFrame myFrame;

  public PySmartStepIntoContext(int startLine, int endLine, @NotNull PyStackFrame frame) {
    myStartLine = startLine;
    myEndLine = endLine;
    myFrame = frame;
  }

  public int getStartLine() {
    return myStartLine;
  }

  public int getEndLine() {
    return myEndLine;
  }

  @NotNull
  public PyStackFrame getFrame() {
    return myFrame;
  }

  /**
   * Compares this context to the specified object. The result is {@code
   * true} if the argument is instance of {@code PySmartStepIntoContext},
   * the line the two contexts were created are the same and either the IDs
   * of the frames they were called are the same or this context frame is
   * the frame of a generator expression. The latest is important because we
   * don't want to make a difference between a generator expression frame and
   * the frame it was called from. Otherwise we would create a new context
   * on each generator iteration.
   *
   * @param  o
   *         The object to compare this {@code PySmartStepIntoContext} against
   *
   * @return  {@code true} if the given object represents a {@code PySmartStepIntoContext}
   *          equivalent to this context, {@code false} otherwise
   *
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PySmartStepIntoContext)) return false;
    PySmartStepIntoContext context = (PySmartStepIntoContext)o;
    return myStartLine == context.myStartLine && myEndLine == context.myEndLine
           && myFrame.getFrameId().equals(context.getFrame().getFrameId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(myStartLine, myFrame);
  }
}
