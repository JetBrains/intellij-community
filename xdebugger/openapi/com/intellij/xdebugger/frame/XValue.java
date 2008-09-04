package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XValue extends XValueContainer {

  /**
   * Start computing presentation of the value in the debugger tree and call {@link XValueNode#setPresentation(String, javax.swing.Icon, String, String, boolean)}
   * when computation is finished.
   * @param node node.
   */
  public abstract void computePresentation(@NotNull XValueNode node);


  /**
   * @return expression which evaluates to the current value
   */
  @Nullable
  public String getEvaluationExpression() {
    return null;
  }

  /**
   * @return {@link com.intellij.xdebugger.frame.XValueModifier} instance which can be used to modify the value
   */
  @Nullable
  public XValueModifier getModifier() {
    return null;
  }

  /**
   * Start computing source position of the value and call {@link XNavigatable#setSourcePosition(com.intellij.xdebugger.XSourcePosition)}
   * when computation is finished 
   * @param navigatable navigatable
   */
  public void computeSourcePosition(@NotNull XNavigatable navigatable) {
    navigatable.setSourcePosition(null);
  }
}
