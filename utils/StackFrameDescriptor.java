package org.jetbrains.debugger.memory.utils;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

public class StackFrameDescriptor {
  private final String myFilePath;
  private final String myMethodName;
  private final int myLineNumber;

  public StackFrameDescriptor(@NotNull String path, @NotNull String methodName, int line) {
    myFilePath = path.replace('\\', '.');
    myMethodName = methodName;
    myLineNumber = line;
  }

  @NotNull
  public String path() {
    return myFilePath;
  }

  @NotNull
  public String methodName() {
    return myMethodName;
  }

  @NotNull
  public String className() {
    return StringUtil.getShortName(myFilePath);
  }

  @NotNull
  public String packageName() {
    return StringUtil.getPackageName(myFilePath);
  }

  public int line() {
    return myLineNumber;
  }
}
