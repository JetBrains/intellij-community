package com.intellij.xdebugger.stepping;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Implement this class and return its instance from {@link com.intellij.xdebugger.XDebugProcess#getSmartStepIntoHandler()} to support
 * Smart Step Into action
 *
 * @author nik
 */
public abstract class XSmartStepIntoHandler<Variant extends XSmartStepIntoVariant> {

  /**
   * @param position current position
   * @return list of function/method calls containing in the current line
   */
  @NotNull
  public abstract List<Variant> computeSmartStepVariants(@NotNull XSourcePosition position);

  /**
   * Resume execution and call {@link com.intellij.xdebugger.XDebugSession#positionReached(com.intellij.xdebugger.frame.XSuspendContext)}
   * when <code>variant</code> function/method is reached
   * @param variant selected variant
   */
  public abstract void startStepInto(Variant variant);

  /**
   * @return title for popup which will be shown to select method/function
   * @param position current position
   */
  public abstract String getPopupTitle(@NotNull XSourcePosition position);
}
