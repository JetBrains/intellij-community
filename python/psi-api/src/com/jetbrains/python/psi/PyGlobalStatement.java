package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyGlobalStatement extends PyStatement, NameDefiner {
  @NotNull PyTargetExpression[] getGlobals();

  void addGlobal(String name);
}
