package com.jetbrains.python.psi.resolve;

public enum PointInImport {
  /**
   * The reference is not inside an import statement.
   */
  NONE,

  /**
   * The reference is inside import and refers to a module
   */
  AS_MODULE,

  /**
   * The reference is inside import and refers to a name imported from a module
   */
  AS_NAME
}
