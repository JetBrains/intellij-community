package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface XNavigatable {

  void setSourcePosition(@Nullable XSourcePosition sourcePosition);

}
