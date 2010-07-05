package com.jetbrains.python.psi;

/**
 * @author yole
 */
public interface PyWithItem extends PyElement {
  PyWithItem[] EMPTY_ARRAY = new PyWithItem[0];

  PyTargetExpression getTargetExpression();
}
