package org.jetbrains.debugger.memory.utils;

import org.jetbrains.annotations.NotNull;

public class StackFrameDescriptor {
  private final String myFilePath;
  private int myFrameLevel;
  private int myLineNumber;

  public StackFrameDescriptor(@NotNull String path, int frameLevel, int line) {
    myFilePath = path.replace('\\', '.');
    myFrameLevel = frameLevel;
    myLineNumber = line;
  }

  @NotNull
  public String path() {
    return myFilePath;
  }

  public int line() {
    return myLineNumber;
  }

  @Override
  public String toString() {
    return String.format("%d. %s:%d", myFrameLevel, myFilePath, myLineNumber);
  }
}
