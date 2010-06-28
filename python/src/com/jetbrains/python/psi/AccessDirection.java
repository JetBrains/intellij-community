package com.jetbrains.python.psi;

/** How we refer to a name */
public enum AccessDirection {

  /** Reference */
  READ,

  /** Target of assignment */
  WRITE,

  /** Target of del statement */
  DELETE;

  /**
   * @param element
   * @return the access direction of element, judging from surrounding statements.
   */
  public static AccessDirection of(PyElement element) {
    AccessDirection ctx;
    if (element instanceof PyTargetExpression) ctx = WRITE;
    else if (element.getParent() instanceof PyDelStatement) ctx = DELETE;
    else ctx = READ;
    return ctx;
  }
}
