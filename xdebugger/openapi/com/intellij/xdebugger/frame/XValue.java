package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class XValue extends XValueContainer {

  public abstract void computePresentation(@NotNull XValueNode node);

  @Nullable
  public String getExpression() {
    return null;
  }
}
