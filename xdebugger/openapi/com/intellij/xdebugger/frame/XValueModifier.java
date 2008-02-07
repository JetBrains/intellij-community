package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XValueModifier {

  /**
   * Start modification of the value.
   * @param expression new value
   * @param callback used to notify that value has been successfully modified or an error occurs
   */
  public abstract void setValue(@NotNull String expression, @NotNull XModificationCallback callback);

  public static interface XModificationCallback {
    void valueModified();

    void errorOccured(@NotNull String errorMessage);
  }
}
