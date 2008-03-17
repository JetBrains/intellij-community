package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.Obsolescent;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

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
   * Start computing stack frames top-down starting from <code>firstFrameIndex</code>   
   * @param firstFrameIndex frame index to start from (<code>1</code> corresponds to the frame just under the top frame)
   * @param container callback
   */
  public abstract void computeStackFrames(int firstFrameIndex, XStackFrameContainer container);

  public static interface XStackFrameContainer extends Obsolescent {
    /**
     * Add stack frames to the list
     * @param stackFrames stack frames to add
     * @param last <code>true</code> if all frames are added
     */
    void addStackFrames(@NotNull List<? extends XStackFrame> stackFrames, final boolean last);

    /**
     * Indicate that an error occurs
     * @param errorMessage message describing the error
     */
    void errorOccured(String errorMessage);

  }
}
