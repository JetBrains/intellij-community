package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.Nullable;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;

/**
 * @author nik
 */
public abstract class XSuspendContext {
  
  @Nullable
  public XDebuggerEvaluator getEvaluator() {
    return null;
  }
}
