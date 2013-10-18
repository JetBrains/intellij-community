package com.jetbrains.python.psi;

import com.intellij.psi.PsiElement;

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
    final PsiElement parent = element.getParent();
    if (element instanceof PyTargetExpression) {
      return WRITE;
    }
    else if (parent instanceof PyAssignmentStatement) {
      for (PyExpression target : ((PyAssignmentStatement)parent).getTargets()) {
        if (target == element) {
          return WRITE;
        }
      }
    }
    else if (parent instanceof PyDelStatement) {
      return DELETE;
    }
    return READ;
  }
}
