package com.jetbrains.python.psi;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PyImportStatementBase extends PyStatement {
  /**
   * @return elements that constitute the "import" clause
   */
  @NotNull
  PyImportElement[] getImportElements();
}
