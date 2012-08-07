package com.jetbrains.python.psi;

import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface PyContinueStatement extends PyStatement {
  @Nullable
  PyLoopStatement getLoopStatement();
}
