package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XExecutionStack {
  public static final XExecutionStack[] EMPTY_ARRAY = new XExecutionStack[0];
  private String myDisplayName;
  private Icon myIcon;

  protected XExecutionStack(final String displayName) {
    this(displayName, DebuggerIcons.SUSPENDED_THREAD_ICON);
  }

  protected XExecutionStack(final @NotNull String displayName, final @Nullable Icon icon) {
    myDisplayName = displayName;
    myIcon = icon;
  }

  @NotNull
  public final String getDisplayName() {
    return myDisplayName;
  }

  @Nullable
  public final Icon getIcon() {
    return myIcon;
  }

  /**
   * Return top stack frame synchronously
   * @return top stack frame or <code>null</code> if it isn't available
   */
  @Nullable
  public abstract XStackFrame getTopFrame();

  /**
   * @return stack frames count
   */
  public abstract int getFramesCount();

  /**
   * Start obtaining stack frame and call <code>callback.{@link com.intellij.xdebugger.frame.XExecutionStack.XStackFrameCallback#stackFrameObtained(XStackFrame)}</code>
   * when stack frame is obtained or <code>callback.{@link com.intellij.xdebugger.frame.XExecutionStack.XStackFrameCallback#errorOccured(String)}</code> if an error occurs.  
   * @param frameIndex frame index (from <code>1</code> to <code>{@link XExecutionStack#getFramesCount()}-1</code>)
   * @param callback callback
   */
  public abstract void obtainStackFrame(int frameIndex, XStackFrameCallback callback);

  public static interface XStackFrameCallback {

    void stackFrameObtained(@NotNull XStackFrame stackFrame);

    void errorOccured(String errorMessage);

  }
}
