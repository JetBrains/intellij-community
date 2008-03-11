package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XExecutionStack {
  public static final XExecutionStack[] EMPTY_ARRAY = new XExecutionStack[0];
  private String myDisplayName;

  protected XExecutionStack(final @NotNull String displayName) {
    myDisplayName = displayName;
  }

  /**
   * Return stack frames from top to bottom
   * @return stack frames
   */
  public abstract XStackFrame[] getStackFrames();

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  public final XStackFrame getTopFrame() {
    XStackFrame[] stackFrames = getStackFrames();
    return stackFrames.length > 0 ? stackFrames[0] : null;
  }
}
