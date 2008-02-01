package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XValue extends XValueContainer {

  public abstract void computePresentation(@NotNull XValueNode node);

}
