package com.jetbrains.python.psi;

/**
 * @author yole
 */
public interface PyImportStatementBase extends PyStatement {
  /**
   * @return elements that constitute the "import" clause
   */
  PyImportElement[] getImportElements();
}
